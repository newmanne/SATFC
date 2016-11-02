package ca.ubc.cs.beta.fcc.simulator.solver.decorator;

import ca.ubc.cs.beta.fcc.simulator.feasibilityholder.IProblemMaker;
import ca.ubc.cs.beta.fcc.simulator.ladder.LadderEventOnMoveDecorator;
import ca.ubc.cs.beta.fcc.simulator.participation.ParticipationRecord;
import ca.ubc.cs.beta.fcc.simulator.solver.IFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SATFCCallback;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SimulatorResult;
import ca.ubc.cs.beta.fcc.simulator.solver.problem.ProblemType;
import ca.ubc.cs.beta.fcc.simulator.solver.problem.SimulatorProblem;
import ca.ubc.cs.beta.fcc.simulator.state.SaveStateToFile;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.station.StationDB;
import ca.ubc.cs.beta.fcc.simulator.time.TimeTracker;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.utils.Watch;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.eventbus.Subscribe;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Created by newmanne on 2016-10-11.
 */
@Slf4j
public class UHFCachingFeasibilitySolverDecorator extends AFeasibilitySolverDecorator {

    ImmutableSet<ProblemType> CACHEABLE_PROBLEMS = ImmutableSet.of(ProblemType.BID_PROCESSING_HOME_BAND_FEASIBLE, ProblemType.BID_PROCESSING_MOVE_FEASIBLE, ProblemType.BID_STATUS_UPDATING_HOME_BAND_FEASIBLE);

    @Data
    public static class UHFCacheEntry {

        public UHFCacheEntry(SimulatorResult result) {
            this.result = result;
            this.hitCount = 0;
        }

        private SimulatorResult result;
        private int hitCount;

    }

    private final Map<IStationInfo, UHFCacheEntry> feasibility;
    private final ParticipationRecord participation;
    private final StationDB stationDB;
    private final IProblemMaker problemMaker;
    private boolean isDirty;
    // If true, then don't prefill the cache every time it dirties - just grab problems one by one, as needed
    private final boolean lazy;
    private final TimeTracker wastedTimeTracker;

    public UHFCachingFeasibilitySolverDecorator(IFeasibilitySolver decorated, ParticipationRecord participation, StationDB stationDB, IProblemMaker problemMaker, boolean lazy) {
        super(decorated);
        feasibility = new ConcurrentHashMap<>();
        this.participation = participation;
        this.stationDB = stationDB;
        this.problemMaker = problemMaker;
        isDirty = true;
        this.lazy = lazy;
        this.wastedTimeTracker = new TimeTracker();
    }

    // This is sort of at the wrong level of abstraction (Because I no longer know the "type" of problem...) oh well...
    @Override
    public void getFeasibility(SimulatorProblem problem, SATFCCallback callback) {
        if (problem.getBand().equals(Band.UHF) && CACHEABLE_PROBLEMS.contains(problem.getProblemType())) {
            if (isDirty) {
                purgeDirty();
                if (!lazy) {
                    rebuildCache();
                }
            }

            final IStationInfo addedStation = problem.getTargetStation();
            final UHFCacheEntry cacheEntry = feasibility.get(addedStation);
            if (lazy && cacheEntry == null) {
                log.trace("Lazy and problem not in cache... Have to add it");
                super.getFeasibility(problem, (p, r) -> {
                    feasibility.put(addedStation, new UHFCacheEntry(r));
                    callback.onSuccess(p, r);
                });
            } else {
                Preconditions.checkNotNull(cacheEntry, "Could not find a result in the UHF station cache for station %s", addedStation);
                SimulatorResult result = cacheEntry.getResult();
                // Accounting: if this is our first time seeing the cache entry, charge the time it took to compute it. Otherwise, charge the lookup time.
                if (lazy || cacheEntry.getHitCount() > 0) {
                    // valid code, unsupported in intellij lombok plugin
                    result = result.toBuilder().cached(true).build();
                }
                cacheEntry.setHitCount(cacheEntry.getHitCount() + 1);
                log.trace("Successful cache hit for {}", problem.getSATFCProblem().getName());
                callback.onSuccess(problem, result);
            }
        } else {
            log.trace("Not a UHF home band or move feasibility problem, skipping cache");
            super.getFeasibility(problem, callback);
        }
    }

    public void rebuildCache() {
        final Set<IStationInfo> stationsToRecompute = participation.getActiveStations().stream()
                .filter(s -> s.getHomeBand().equals(Band.UHF))
                .filter(s -> !feasibility.containsKey(s))
                .collect(Collectors.toSet());
        log.info("Recomputing cache... {} problems", stationsToRecompute.size());
        for (final IStationInfo s: stationsToRecompute) {
            decorated.getFeasibility(problemMaker.makeProblem(s, Band.UHF, ProblemType.UHF_CACHE_PREFILL), new SATFCCallback() {
                @Override
                public void onSuccess(SimulatorProblem problem, SimulatorResult result) {
                    feasibility.put(s, new UHFCacheEntry(result));
                }
            });
        }
        decorated.waitForAllSubmitted();
        log.info("Cache recomputed");
    }

    private void purgeDirty() {
        // Remove any stale info (If we wanted to be a bit better, we could only remove stations in the connected component of the band a station moved in to, but it doesn't seem worth the complexity of coding)
        // Note that a station cannot suddenly become feasible if more stations moved into the band, so we should keep UNSAT results
        final Iterator<Map.Entry<IStationInfo, UHFCacheEntry>> iterator = feasibility.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<IStationInfo, UHFCacheEntry> entry = iterator.next();
            final SimulatorResult result = entry.getValue().getResult();
            if (!result.getSATFCResult().getResult().equals(SATResult.UNSAT)) {
                // If we didn't use the result, add its computation time to "extra"
                if (entry.getValue().getHitCount() == 0) {
                    wastedTimeTracker.update(result.getSATFCResult());
                }
                iterator.remove();
            }
        }
        isDirty = false;
    }

    /**
     * If a station moves into UHF, we need to clear all of our results and recompute them
     */
    @Subscribe
    public void onMove(LadderEventOnMoveDecorator.LadderMoveEvent moveEvent) {
        if (moveEvent.getNewBand().equals(Band.UHF)) {
            log.info("Station {} moved into UHF, marking UHF cache as dirty", moveEvent.getStation());
            isDirty = true;
        }
    }

    @Subscribe
    public void onReportState(SaveStateToFile.ReportStateEvent reportStateEvent) {
        final SaveStateToFile.UHFCacheState uhfCacheState = SaveStateToFile.UHFCacheState.builder()
                .wastedNProblems(wastedTimeTracker.getNProblems().get())
                .wastedProblemCPUTime(wastedTimeTracker.getCputime().get())
                .wastedProblemWallTime(wastedTimeTracker.getWalltime().get())
                .build();
        reportStateEvent.getBuilder().uhfCacheState(uhfCacheState);
    }

}
