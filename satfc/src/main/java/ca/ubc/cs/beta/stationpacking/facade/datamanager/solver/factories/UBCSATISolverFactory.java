package ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories;

import lombok.RequiredArgsConstructor;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.solvers.sat.CompressedSATBasedSolver;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.SATCompressor;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.AbstractCompressedSATSolver;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.nonincremental.ubcsat.UBCSATSolver;

/**
 * Created by newmanne on 03/09/15.
 */
@RequiredArgsConstructor
public class UBCSATISolverFactory {

    private final UBCSATLibraryGenerator libraryGenerator;
    private final SATCompressor satCompressor;
    private final IConstraintManager constraintManager;

    public CompressedSATBasedSolver create(String aConfig) {
        final AbstractCompressedSATSolver SATSolver = new UBCSATSolver(libraryGenerator.createLibrary(), aConfig);
        return new CompressedSATBasedSolver(SATSolver, satCompressor);
    }

}
