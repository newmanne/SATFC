package ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories;

import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.ClaspLibraryGenerator;
import ca.ubc.cs.beta.stationpacking.solvers.sat.CompressedSATBasedSolver;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.SATCompressor;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.AbstractCompressedSATSolver;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.nonincremental.Clasp3SATSolver;
import lombok.RequiredArgsConstructor;

/**
* Created by newmanne on 03/06/15.
*/
@RequiredArgsConstructor
public class Clasp3ISolverFactory {

    private final ClaspLibraryGenerator claspLibraryGenerator;
    private final SATCompressor satCompressor;
    private final IConstraintManager constraintManager;

    public CompressedSATBasedSolver create(String aConfig) {
        return create(aConfig, 0);
    }

    public CompressedSATBasedSolver create(String aConfig, int seedOffset) {
        final AbstractCompressedSATSolver claspSATsolver = new Clasp3SATSolver(claspLibraryGenerator.createClaspLibrary(), aConfig, seedOffset);
        return new CompressedSATBasedSolver(claspSATsolver, satCompressor, constraintManager);
    }

}
