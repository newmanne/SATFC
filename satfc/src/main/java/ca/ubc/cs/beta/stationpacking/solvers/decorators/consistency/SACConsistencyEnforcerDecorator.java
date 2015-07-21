package ca.ubc.cs.beta.stationpacking.solvers.decorators.consistency;

import java.util.HashMap;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.consistency.AC3Enforcer;
import ca.ubc.cs.beta.stationpacking.consistency.AC3Output;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.ASolverDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.utils.Watch;

import com.google.common.collect.Sets;

/**
 * Created by newmanne on 10/06/15.
 */
@Slf4j
public class SACConsistencyEnforcerDecorator extends ASolverDecorator {

    private final AC3Enforcer ac3Enforcer;

    /**
     * @param aSolver - decorated ISolver.
     */
    public SACConsistencyEnforcerDecorator(ISolver aSolver, IConstraintManager constraintManager) {
        super(aSolver);
        ac3Enforcer = new AC3Enforcer(constraintManager);
    }

    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
        final Watch watch = Watch.constructAutoStartWatch();
        // First, run AC3
        final AC3Output ac3Output = ac3Enforcer.AC3(aInstance);
        if (ac3Output.isNoSolution()) {
            return new SolverResult(SATResult.UNSAT, watch.getElapsedTime());
        } else {
            final StationPackingInstance reducedInstance = new StationPackingInstance(ac3Output.getReducedDomains(), aInstance.getPreviousAssignment(), aInstance.getMetadata());
            // SAC!
            final HashMap<Station, Set<Integer>> reducedDomain = new HashMap<>(reducedInstance.getDomains());
            reducedDomain.keySet().forEach(station -> reducedDomain.replace(station, Sets.newHashSet(reducedInstance.getDomains().get(station))));
            for (Station station : reducedInstance.getStations()) {
                log.info("Starting station {}", station);
                for (Integer channel : reducedInstance.getDomains().get(station)) {
                    // restrict domain to a single value
                    final Set<Integer> singletonDomain = Sets.newHashSet(channel);
                    final HashMap<Station, Set<Integer>> stationSetHashMap = new HashMap<>(reducedDomain);
                    stationSetHashMap.remove(station);
                    stationSetHashMap.put(station, singletonDomain);
                    final AC3Output reducedAC3Output = ac3Enforcer.AC3(new StationPackingInstance(stationSetHashMap, aInstance.getPreviousAssignment(), aInstance.getMetadata()));
                    if (reducedAC3Output.isNoSolution()) {
                        final Set<Integer> integers = reducedDomain.get(station);
                        integers.remove(channel);
                        log.info("Removing channel {} from station {}'s domain", channel, station);
                        if (integers.isEmpty()) {
                            return new SolverResult(SATResult.UNSAT, watch.getElapsedTime());
                        }
                    }
                }
            }
            return SolverResult.addTime(fDecoratedSolver.solve(reducedInstance, aTerminationCriterion, aSeed), watch.getElapsedTime());
        }
    }


}
