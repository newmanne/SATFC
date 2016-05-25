package ca.ubc.cs.beta.fcc.simulator.solver.problem;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader;
import com.google.common.collect.Maps;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by newmanne on 2016-05-25.
 */
public class ProblemGeneratorImpl implements IProblemGenerator {

    private final Map<Integer, Set<Integer>> domains;

    public ProblemGeneratorImpl(int maxChannel, IStationManager stationManager) {
        domains = new HashMap<>();
        for (Station s : stationManager.getStations()) {
            domains.put(s.getID(), stationManager.getRestrictedDomain(s, maxChannel, true));
        }
    }

    @Override
    public SimulatorProblemReader.SATFCProblem createProblem(Set<Integer> stations, Map<Integer, Integer> previousAssignment) {
        return new SimulatorProblemReader.SATFCProblem(
                Maps.filterKeys(domains, stations::contains),
                previousAssignment
        );
    }

}
