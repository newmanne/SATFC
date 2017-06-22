package ca.ubc.cs.beta.fcc.simulator.solver.problem;

import ca.ubc.cs.beta.fcc.simulator.Simulator;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.SimulatorUtils;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.Set;

/**
 * Created by newmanne on 2016-05-25.
 */
@RequiredArgsConstructor
public class SATFCProblemSpecGeneratorImpl implements Simulator.ISATFCProblemSpecGenerator {

    private final String stationInfoFolder;
    private final double cutoff;
    private final long seed;

    @Override
    public SimulatorProblemReader.SATFCProblemSpecification createProblem(SimulatorProblemReader.SATFCProblem problem, String name) {
        return new SimulatorProblemReader.SATFCProblemSpecification(
                problem,
                cutoff,
                stationInfoFolder,
                seed,
                name);
    }

}
