package ca.ubc.cs.beta.fcc.simulator.solver.decorator;

import ca.ubc.cs.beta.fcc.simulator.DomainChangeEvent;
import ca.ubc.cs.beta.fcc.simulator.feasibilityholder.IProblemMaker;
import ca.ubc.cs.beta.fcc.simulator.ladder.ILadder;
import ca.ubc.cs.beta.fcc.simulator.ladder.LadderEventOnMoveDecorator;
import ca.ubc.cs.beta.fcc.simulator.participation.ParticipationRecord;
import ca.ubc.cs.beta.fcc.simulator.solver.IFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SATFCCallback;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SimulatorResult;
import ca.ubc.cs.beta.fcc.simulator.solver.problem.ProblemType;
import ca.ubc.cs.beta.fcc.simulator.solver.problem.SimulatorProblem;
import ca.ubc.cs.beta.fcc.simulator.state.SaveStateToFile;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.time.TimeTracker;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.fcc.simulator.utils.SimulatorUtils;
import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.ConstraintGrouper;
import ca.ubc.cs.beta.stationpacking.utils.Watch;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.eventbus.Subscribe;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ca.ubc.cs.beta.stationpacking.utils.GuavaCollectors.toImmutableMap;

/**
 * Created by newmanne on 2016-10-11.
 * // TODO: Add the ability to revisit TIMEOUT results (i.e. delete them whenever anyone moves to UHF)
 */
@Slf4j
public class UHFCachingFeasibilitySolverDecorator extends AFeasibilitySolverDecorator {

    final private static ImmutableSet<ProblemType> CACHEABLE_PROBLEMS = ImmutableSet.of(ProblemType.BID_PROCESSING_HOME_BAND_FEASIBLE, ProblemType.BID_PROCESSING_MOVE_FEASIBLE, ProblemType.BID_STATUS_UPDATING_HOME_BAND_FEASIBLE, ProblemType.BETWEEN_STAGE_HOME_BAND_FEASIBLE, ProblemType.CATCHUP_FEASIBLE);

    @Data
    public static class UHFCacheEntry {

        public UHFCacheEntry(SimulatorProblem problem, SimulatorResult result) {
            this.result = result;
            this.hitCount = 0;
            this.cutoff = problem.getSATFCProblem().getCutoff();
        }

        private final SimulatorResult result;
        private int hitCount;
        private final double cutoff;

    }

    private final Map<IStationInfo, UHFCacheEntry> feasibility;
    private final ParticipationRecord participation;
    private final IProblemMaker problemMaker;
    // If true, then don't prefill the cache every time it dirties - just grab problems one by one, as needed
    private final boolean lazy;
    // If false, when a result times out in UHF, it will remain a timeout forever. If true, it gets retried when a new station moves into the band
    private final boolean revisitTimeouts;
    private final TimeTracker wastedTimeTracker;

    private ImmutableMap<IStationInfo, Set<IStationInfo>> stationToComponent;
    private boolean needsRebuid;

    private final GreedyFlaggingDecorator greedyFlaggingDecorator;

    public UHFCachingFeasibilitySolverDecorator(IFeasibilitySolver decorated, ParticipationRecord participation, IProblemMaker problemMaker, boolean lazy, boolean revisitTimeouts, ILadder ladder, IConstraintManager constraintManager) {
        super(decorated);
        feasibility = new ConcurrentHashMap<>();
        this.participation = participation;
        this.problemMaker = problemMaker;
        this.lazy = lazy;
        this.wastedTimeTracker = new TimeTracker();
        needsRebuid = true;
        this.revisitTimeouts = revisitTimeouts;
        // We maintain a separate decorator here literally just for flagging cached problems
        this.greedyFlaggingDecorator = new GreedyFlaggingDecorator(new VoidFeasibilitySolver(), constraintManager);
    }

    public void init(ILadder ladder, IConstraintManager constraintManager) {
        // Get the connected components in UHF
        final Map<Station, Set<Integer>> domains = ladder.getStations().stream()
                .collect(Collectors.toMap(IStationInfo::toSATFCStation, s -> s.getDomain(Band.UHF)));
        final Map<Station, IStationInfo> stationToInfo = ladder.getStations().stream().collect(Collectors.toMap(IStationInfo::toSATFCStation, Function.identity()));
        final SimpleGraph<IStationInfo, DefaultEdge> constraintGraph = ConstraintGrouper.getConstraintGraph(domains, constraintManager, stationToInfo);
        final ConnectivityInspector<IStationInfo, DefaultEdge> connectivityInspector = new ConnectivityInspector<>(constraintGraph);
        final Set<Set<IStationInfo>> connectedComponents = new HashSet<>(connectivityInspector.connectedSets());
        stationToComponent = ladder.getStations().stream().collect(toImmutableMap(Function.identity(), s -> connectedComponents.stream().filter(component -> component.contains(s)).findFirst().get()));
        greedyFlaggingDecorator.init(ladder);
    }

    @Override
    public void getFeasibility(SimulatorProblem problem, SATFCCallback callback) {
        if (problem.getBand().equals(Band.UHF) && CACHEABLE_PROBLEMS.contains(problem.getProblemType())) {
            if (!lazy && needsRebuid) {
                rebuildCache();
            }

            final IStationInfo addedStation = problem.getTargetStation();
            final UHFCacheEntry cacheEntry = feasibility.get(addedStation);
            if (lazy && cacheEntry == null) {
                log.trace("Lazy and problem not in cache... Have to add it");
                solveProblemAndUpdateCache(problem, callback, addedStation);
            } else {
                Preconditions.checkNotNull(cacheEntry, "Could not find a result in the UHF station cache for station %s", addedStation);
                SimulatorResult result = cacheEntry.getResult();

                // If you have a TIMEOUT result in the cache, but this time around, we are giving a larger time budget, then do NOT use the cached result!!!
                if (result.getSATFCResult().getResult().equals(SATResult.TIMEOUT) &&
                        problem.getSATFCProblem().getCutoff() > cacheEntry.getCutoff()) {
                    log.trace("Revising cache entry in light of more time offered for problem");
                    solveProblemAndUpdateCache(problem, callback, addedStation);
                } else {
                    // Accounting: if this is our first time seeing the cache entry, charge the time it took to compute it. Otherwise, charge the lookup time.
                    if (lazy || cacheEntry.getHitCount() > 0) {
                        // valid code, unsupported in intellij lombok plugin
                        result = result.toBuilder().cached(true).build();
                    }
                    // We want to know if a greedy solver COULD have solved the initial problem (it won't get there due to decorator ordering)
                    if (greedyFlaggingDecorator.getFeasibilityBlocking(problem).isGreedySolved()) {
                        result = result.toBuilder().greedySolved(true).build();
                    }
                    cacheEntry.setHitCount(cacheEntry.getHitCount() + 1);
                    log.trace("Successful cache hit for {}", problem.getSATFCProblem().getName());
                    callback.onSuccess(problem, result);
                }
            }
        } else {
            log.trace("Not a UHF home band or move feasibility problem, skipping cache");
            super.getFeasibility(problem, callback);
        }
    }

    private void solveProblemAndUpdateCache(SimulatorProblem problem, SATFCCallback callback, IStationInfo addedStation) {
        super.getFeasibility(problem, (p, r) -> {
            feasibility.put(addedStation, new UHFCacheEntry(p, r));
            callback.onSuccess(p, r);
        });
    }

    public void rebuildCache() {
        final Set<IStationInfo> stationsToRecompute = participation.getActiveStations().stream()
                .filter(s -> s.getHomeBand().equals(Band.UHF))
                .filter(s -> !feasibility.containsKey(s))
                .collect(Collectors.toSet());
        log.info("Recomputing cache... {} problems", stationsToRecompute.size());
        for (final IStationInfo s : stationsToRecompute) {
            decorated.getFeasibility(problemMaker.makeProblem(s, Band.UHF, ProblemType.UHF_CACHE_PREFILL), new SATFCCallback() {
                @Override
                public void onSuccess(SimulatorProblem problem, SimulatorResult result) {
                    feasibility.put(s, new UHFCacheEntry(problem, result));
                }
            });
        }
        decorated.waitForAllSubmitted();
        log.info("Cache recomputed");
        needsRebuid = false;
    }

    private void purgeDirty(IStationInfo movedStation, Map<Integer, Integer> mostRecentAssignment) {
        // Remove any stale info - any station in the connected component of the UHF interference graph of the moved station is now dirty
        // Note that a station cannot suddenly become feasible if more stations moved into the band, so we should keep UNSAT results
        final Set<IStationInfo> componentStations = stationToComponent.get(movedStation);
        final Iterator<Map.Entry<IStationInfo, UHFCacheEntry>> iterator = feasibility.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<IStationInfo, UHFCacheEntry> entry = iterator.next();
            final SimulatorResult result = entry.getValue().getResult();
            if (SimulatorUtils.isFeasible(result.getSATFCResult())) {
                if (componentStations.contains(entry.getKey())) {
                    // If we didn't use the result, add its computation time to "extra"
                    if (entry.getValue().getHitCount() == 0) {
                        wastedTimeTracker.update(result.getSATFCResult());
                    }
                    // Delete SAT results within the component
                    iterator.remove();
                } else {
                    Preconditions.checkState(result.getSATFCResult().getResult().equals(SATResult.SAT), "Trying to augment a non-SAT result!");
                    // This is a SAT answer for a station not in the affected component. We need to alter / augment all of the values in the altered component
                    final SATFCResult satfcResult = entry.getValue().getResult().getSATFCResult();
                    final Map<Integer, Integer> assignment = new HashMap<>(satfcResult.getWitnessAssignment());
                    for (IStationInfo station : componentStations) {
                        final Integer newAssignedChannel = mostRecentAssignment.get(station.getId());
                        if (newAssignedChannel != null) {
                            // Update their channels
                            assignment.put(station.getId(), newAssignedChannel);
                        }
                    }
                    // update assignment
                    entry.getValue().getResult().setSATFCResult(new SATFCResult(satfcResult.getResult(), satfcResult.getRuntime(), assignment, satfcResult.getCputime(), satfcResult.getExtraInfo()));
                }
            } else if (revisitTimeouts && result.getSATFCResult().getResult().equals(SATResult.TIMEOUT)) {
                // If the revisit timeouts flag is set, then delete all timeout results whenver a station moves into UHF. We could probably limit this to whenever a UHF station moves into the same component if we used a SATFC configuration that always reduces to by component
                iterator.remove();
            }
        }
    }

    public void clear() {
        feasibility.clear();
    }

    @Subscribe
    public void onDomainChanged(DomainChangeEvent domainChangeEvent) {
        log.debug("Domain has been changed. Flushing UNSAT and TIMEOUT results from the cache and updating component graph");
        init(domainChangeEvent.getLadder(), domainChangeEvent.getConstraintManager());
        final Set<IStationInfo> keysToRemove = feasibility.entrySet().stream()
                .filter(entry -> !entry.getValue().getResult().getSATFCResult().getResult().equals(SATResult.SAT))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        feasibility.keySet().removeAll(keysToRemove);
    }


    /**
     * If a station moves into UHF, we need to clear all of our results and recompute them
     */
    @Subscribe
    public void onMove(LadderEventOnMoveDecorator.LadderMoveEvent moveEvent) {
        if (moveEvent.getNewBand().equals(Band.UHF)) {
            log.debug("Station {} moved into UHF, marking UHF cache as dirty", moveEvent.getStation());
            final Watch purgeWatch = Watch.constructAutoStartWatch();
            purgeDirty(moveEvent.getStation(), moveEvent.getLadder().getPreviousAssignment(Band.UHF));
            needsRebuid = true;
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
