package ca.ubc.cs.beta.fcc.simulator.solver.decorator;

import ca.ubc.cs.beta.fcc.simulator.feasibilityholder.IFeasibilityStateHolder;
import ca.ubc.cs.beta.fcc.simulator.ladder.ILadder;
import ca.ubc.cs.beta.fcc.simulator.ladder.ILadderEventOnMoveListener;
import ca.ubc.cs.beta.fcc.simulator.participation.ParticipationRecord;
import ca.ubc.cs.beta.fcc.simulator.solver.IFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SATFCCallback;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.station.StationDB;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.fcc.simulator.utils.BandHelper;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.utils.Watch;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Created by newmanne on 2016-10-11.
 */
@Slf4j
public class UHFCachingFeasibilitySolverDecorator extends AFeasibilitySolverDecorator implements ILadderEventOnMoveListener {

    private final Map<IStationInfo, SATFCResult> feasibility;
    private final ILadder ladder;
    private final ParticipationRecord participation;
    private final StationDB stationDB;
    private final IFeasibilityStateHolder problemMaker;
    private boolean isDirty;

    public UHFCachingFeasibilitySolverDecorator(IFeasibilitySolver decorated, ILadder ladder, ParticipationRecord participation, StationDB stationDB, IFeasibilityStateHolder problemMaker) {
        super(decorated);
        feasibility = new ConcurrentHashMap<>();
        this.ladder = ladder;
        this.participation = participation;
        this.stationDB = stationDB;
        this.problemMaker = problemMaker;
        isDirty = true;
    }

    // This is sort of at the wrong level of abstraction (Because I no longer know the "type" of problem...) oh well...
    @Override
    public void getFeasibility(SimulatorProblemReader.SATFCProblemSpecification problem, SATFCCallback callback) {
        final Watch watch = Watch.constructAutoStartWatch();
        final List<String> splits = Splitter.on('_').splitToList(problem.getName());
        // Pretty hacky...
        boolean appropriateProblem = splits.size() == 4 &&
                (splits.get(1).equals(IFeasibilityStateHolder.BID_PROCESSING_HOME_BAND_FEASIBILITY) || splits.get(1).equals(IFeasibilityStateHolder.BID_STATUS_UPDATING_HOME_BAND_FEASIBILITY) ||  splits.get(1).equals(IFeasibilityStateHolder.BID_PROCESSING_MOVE_FEASIBILITY))
                && splits.get(3).equals(Band.UHF.toString());
        if (!appropriateProblem) {
            log.trace("Not a UHF home band or move feasibility problem, skipping cache");
            super.getFeasibility(problem,callback);
            return;
        }
        if (isDirty) {
            rebuildCache();
        }

        // Step 2: Identify the "added" station
        final Sets.SetView<Integer> stationsNewToBand = Sets.difference(problem.getProblem().getDomains().keySet(), problem.getProblem().getPreviousAssignment().keySet());
        Preconditions.checkState(stationsNewToBand.size() == 1, "More than 1 station lacking a previous assignment! (%s). Problem: %s Previous Assignment: %s", stationsNewToBand, problem.getProblem().getDomains(), problem.getProblem().getPreviousAssignment());
        final int idOfAddedStation = stationsNewToBand.iterator().next();
        final IStationInfo addedStation = stationDB.getStationById(idOfAddedStation);

        // Step 3: Is the value cached? Return that
        final SATFCResult result = feasibility.get(addedStation);
        Preconditions.checkNotNull(result, "Could not find a result in the UHF station cache for station %s", addedStation);
        final SATFCResult resultTimeAdjusted = new SATFCResult(result.getResult(), watch.getElapsedTime(), watch.getElapsedTime(), result.getWitnessAssignment());
        log.trace("Successful cache hit for {}", problem.getName());
        callback.onSuccess(problem, resultTimeAdjusted);
    }

    public void rebuildCache() {
        // Remove any stale info (If we wanted to be a bit better, we could only remove stations in the connected component of the band a station moved in to, but it doesn't seem worth the complexity of coding)
        // Note that a station cannot suddenly become feasible if more stations moved into the band, so we should keep UNSAT results
        feasibility.entrySet().removeIf(entry -> !entry.getValue().getResult().equals(SATResult.UNSAT));
        final Set<IStationInfo> stationsToRecompute = participation.getActiveStations().stream()
                .filter(s -> s.getHomeBand().equals(Band.UHF))
                .filter(s -> !feasibility.containsKey(s))
                .collect(Collectors.toSet());
        log.info("Recomputing cache... {} problems", stationsToRecompute.size());
        for (final IStationInfo s: stationsToRecompute) {
            decorated.getFeasibility(problemMaker.makeProblem(s, Band.UHF, ""), new SATFCCallback() {
                @Override
                public void onSuccess(SimulatorProblemReader.SATFCProblemSpecification problem, SATFCResult result) {
                    feasibility.put(s, result);
                }
            });
        }
        decorated.waitForAllSubmitted();
        log.info("Cache recomputed");
        isDirty = false;
    }

    /**
     * If a station moves into UHF, we need to clear all of our results and recompute them
     */
    @Override
    public void onMove(IStationInfo station, Band prevBand, Band newBand, ILadder ladder) {
        if (newBand.equals(Band.UHF)) {
            log.info("Station {} moved into UHF, marking UHF cache as dirty", station);
            isDirty = true;
        }
    }

}
