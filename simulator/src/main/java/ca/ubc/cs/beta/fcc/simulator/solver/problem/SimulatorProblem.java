package ca.ubc.cs.beta.fcc.simulator.solver.problem;

import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader;
import lombok.Builder;
import lombok.Data;

/**
 * Created by newmanne on 2016-11-02.
 */
@Builder
@Data
public class SimulatorProblem {
    private SimulatorProblemReader.SATFCProblemSpecification SATFCProblem;
    private int round;
    private Band band;
    private IStationInfo targetStation;
    private ProblemType problemType;
    private int problemNumber;
}
