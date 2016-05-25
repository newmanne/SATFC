package ca.ubc.cs.beta.fcc.simulator.solver.problem;

import ca.ubc.cs.beta.fcc.simulator.Simulator;
import ca.ubc.cs.beta.fcc.simulator.station.StationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.SimulatorUtils;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.Set;

/**
 * Created by newmanne on 2016-05-25.
 */
@RequiredArgsConstructor
public class ISATFCProblemSpecGeneratorImpl implements Simulator.ISATFCProblemSpecGenerator {

    private final IProblemGenerator problemGenerator;
    private final String stationInfoFolder;
    private final double cutoff;
    private final long seed;

    @Override
    public SimulatorProblemReader.SATFCProblemSpecification createProblem(Set<StationInfo> stationInfos, Map<Integer, Integer> previousAssignment) {
        final Set<Integer> stations = SimulatorUtils.toID(stationInfos);
        final SimulatorProblemReader.SATFCProblem problem = problemGenerator.createProblem(stations, previousAssignment);
        return new SimulatorProblemReader.SATFCProblemSpecification(
                problem,
                cutoff,
                stationInfoFolder,
                seed);
    }

}
