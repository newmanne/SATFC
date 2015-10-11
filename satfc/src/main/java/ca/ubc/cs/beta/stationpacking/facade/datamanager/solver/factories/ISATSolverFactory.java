package ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories;

import ca.ubc.cs.beta.stationpacking.solvers.sat.CompressedSATBasedSolver;

/**
 * Created by newmanne on 10/10/15.
 */
public interface ISATSolverFactory {

    CompressedSATBasedSolver create(String params);

}
