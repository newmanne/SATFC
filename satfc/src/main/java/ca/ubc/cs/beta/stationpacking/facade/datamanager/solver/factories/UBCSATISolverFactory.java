package ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories;

import lombok.RequiredArgsConstructor;
import ca.ubc.cs.beta.stationpacking.solvers.sat.CompressedSATBasedSolver;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.SATCompressor;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.AbstractCompressedSATSolver;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.nonincremental.ubcsat.UBCSATSolver;
import ca.ubc.cs.beta.stationpacking.solvers.termination.interrupt.IPollingService;

/**
 * Created by newmanne on 03/09/15.
 */
@RequiredArgsConstructor
public class UBCSATISolverFactory implements ISATSolverFactory {

    private final UBCSATLibraryGenerator libraryGenerator;
    private final SATCompressor satCompressor;
    private final IPollingService pollingService;

    @Override
    public CompressedSATBasedSolver create(String params, int seedOffset) {
        final AbstractCompressedSATSolver SATSolver = new UBCSATSolver(libraryGenerator.createLibrary(), params, seedOffset, pollingService);
        return new CompressedSATBasedSolver(SATSolver, satCompressor);
    }

}
