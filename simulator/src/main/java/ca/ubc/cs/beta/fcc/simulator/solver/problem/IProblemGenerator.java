package ca.ubc.cs.beta.fcc.simulator.solver.problem;

import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader;
import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Set;

/**
 * Created by newmanne on 2016-05-25.
 */
public interface IProblemGenerator {

    default SimulatorProblemReader.SATFCProblem createProblem(Set<Integer> stations) {
        return createProblem(stations, ImmutableMap.of());
    }

    SimulatorProblemReader.SATFCProblem createProblem(Set<Integer> stations, Map<Integer, Integer> previousAssignment);

}
