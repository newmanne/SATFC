package ca.ubc.cs.beta.stationpacking.solvers.decorators.cache;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.cache.CacheEntry;
import ca.ubc.cs.beta.stationpacking.cache.ICacher;
import ca.ubc.cs.beta.stationpacking.cache.SubsetCache;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.ASolverDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.utils.Watch;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;

import java.util.BitSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
* Created by newmanne on 28/01/15.
*/
@Slf4j
public class SubsetCacheSATDecorator extends ASolverDecorator {

    private final SubsetCache subsetCache;
    private final ICacher cacher;

    public SubsetCacheSATDecorator(ISolver aSolver, SubsetCache subsetCache, ICacher cacher) {
        super(aSolver);
        this.subsetCache = subsetCache;
        this.cacher = cacher;
    }

    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
        Watch watch = Watch.constructAutoStartWatch();
        log.trace("Converting instance to bitset");
        final BitSet aBitSet = aInstance.toBitSet();

        // test sat cache - supersets of the problem that are SAT directly correspond to solutions to the current problem!
        final Optional<SubsetCache.PrecacheSupersetEntry> supersetResult = subsetCache.findSuperset(aBitSet);
        final SolverResult result;
        if (supersetResult.isPresent()) {
            log.info("Found a superset in the SAT cache - declaring result SAT");
            // yay! problem is SAT! Now let's look it up
            final CacheEntry entry = cacher.getSolverResultByKey(supersetResult.get().getKey()).get();

            // convert the answer to that problem into an answer for this problem
            final ImmutableMap<Integer, Set<Station>> assignment = entry.getSolverResult().getAssignment();
            final Map<Integer, Set<Station>> reducedAssignment = Maps.newHashMap();
            for (Integer channel : assignment.keySet()) {
                for (Station station : assignment.get(channel)) {
                    if (aInstance.getStations().contains(station)) {
                        if (reducedAssignment.get(channel) == null) {
                            reducedAssignment.put(channel, Sets.newHashSet());
                        }
                        reducedAssignment.get(channel).add(station);
                    }
                }
            }

            result = new SolverResult(SATResult.SAT, watch.getElapsedTime(), reducedAssignment);
        } else {
            // perhaps we can still find a good place to start a local search by finding a min-hamming distance element in the SAT cache, but this is still a TODO:
            result = fDecoratedSolver.solve(aInstance, aTerminationCriterion, aSeed);
        }
        return result;
    }

}
