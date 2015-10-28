package ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.yaml;

import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.YAMLBundle;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.VoidSolver;

/**
* Created by newmanne on 27/10/15.
*/
public interface ISolverConfig {
    /**
     * Decorate an existing solver with a new solver created from the config object and the context
     */
    ISolver createSolver(YAMLBundle.SATFCContext context, ISolver solverToDecorate);
    default ISolver createSolver(YAMLBundle.SATFCContext context) {
        return createSolver(context, new VoidSolver());
    }

    /**
     * True if the configuration mentioned in the config file should be skipped
     */
    default boolean shouldSkip(YAMLBundle.SATFCContext context) { return false; }
}
