package ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories;

import ca.ubc.cs.beta.stationpacking.solvers.sat.CompressedSATBasedSolver;

/**
 * Created by newmanne on 10/10/15.
 */
public interface ISATSolverFactory {

    /**
     * @param seedOffset SATFC as a whole takes a given seed. This says how much to offset this particular instance against that seed.
     *                   This allows us to run the same SAT solver with different seeds even though SATFC only takes one seed
     */
    CompressedSATBasedSolver create(String params, int seedOffset);

    default CompressedSATBasedSolver create(String params) {
        return create(params, 0);
    };

}
