package ca.ubc.cs.beta.fcc.simulator;

import ca.ubc.cs.beta.fcc.simulator.parameters.SimulatorParameters;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BidProcessingFinishedEvent {

    SimulatorParameters.BidProcessingAlgorithmParameters parameters;

}
