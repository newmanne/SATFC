package ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.nonincremental;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.execution.parameters.solver.sat.UBCSATLibSATSolverParameters;
import ca.ubc.cs.beta.stationpacking.facade.SATFCFacadeBuilder;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.ASolverBundle;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.sat.CompressedSATBasedSolver;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.SATCompressor;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.AbstractCompressedSATSolver;

/**
 * TODO: Add a constructor that allows the user to specify parameters other than default SATenstein.
 * @author pcernek
 */
public class UBCSATSolverBundle extends ASolverBundle {

    ISolver solver;
    final String libraryPath = SATFCFacadeBuilder.findSATFCLibrary(SATFCFacadeBuilder.SATFCLibLocation.CLASP);

    /**
     * Create an abstract solver bundle with the given data management objects.
     *
     * @param aStationManager    - manages stations.
     * @param aConstraintManager - manages constraints.
     */
    public UBCSATSolverBundle(IStationManager aStationManager, IConstraintManager aConstraintManager) {
        super(aStationManager, aConstraintManager);

        SATCompressor aCompressor = new SATCompressor(this.getConstraintManager());

        AbstractCompressedSATSolver ubcsatSolver = new UBCSATSolver(SATFCFacadeBuilder.findSATFCLibrary(SATFCFacadeBuilder.SATFCLibLocation.UBCSAT), UBCSATLibSATSolverParameters.DEFAULT_SATENSTEIN);
        solver = new CompressedSATBasedSolver(ubcsatSolver, aCompressor, aConstraintManager);
    }

    @Override
    public ISolver getSolver(StationPackingInstance aInstance) {
        return solver;
    }

    @Override
    public void close() throws Exception {
    	solver.notifyShutdown();
    }
    
}
