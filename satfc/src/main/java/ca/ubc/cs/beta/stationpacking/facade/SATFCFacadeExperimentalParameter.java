package ca.ubc.cs.beta.stationpacking.facade;

import ca.ubc.cs.beta.stationpacking.execution.parameters.smac.SATFCHydraParams;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.CNFSaverSolverDecorator;
import lombok.experimental.Builder;

/**
* Created by newmanne on 14/08/15.
*/
@Builder
public class SATFCFacadeExperimentalParameter {
    private SATFCHydraParams hydraParams;
    private CNFSaverSolverDecorator.ICNFSaver CNFSaver;
    private boolean presolve;
    private boolean underconstrained;
    private boolean decompose;
}
