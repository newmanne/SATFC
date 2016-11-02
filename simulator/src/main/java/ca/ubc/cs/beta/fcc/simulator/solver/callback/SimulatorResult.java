package ca.ubc.cs.beta.fcc.simulator.solver.callback;

import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
import lombok.Builder;
import lombok.Data;

/**
 * Created by newmanne on 2016-11-01.
 */
@Data
@Builder(toBuilder = true)
public class SimulatorResult {

    private SATFCResult SATFCResult;
    private boolean cached;
    private boolean greedySolved;

    public static SimulatorResult fromSATFCResult(SATFCResult result) {
        return SimulatorResult.builder().SATFCResult(result).build();
    }

}
