package ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.nonincremental;

import lombok.extern.slf4j.Slf4j;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.execution.parameters.solver.sat.UBCSATLibSATSolverParameters;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.ASolverBundle;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories.UBCSATISolverFactory;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories.UBCSATLibraryGenerator;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.AssignmentVerifierDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.SATCompressor;

/**
 * TODO: Add a constructor that allows the user to specify parameters other than default SATenstein.
 * @author pcernek
 */
@Slf4j
public class UBCSATSolverBundle extends ASolverBundle {

    ISolver solver;

    /**
     * Create an abstract solver bundle with the given data management objects.
     *
     * @param aStationManager    - manages stations.
     * @param aConstraintManager - manages constraints.
     */
    public UBCSATSolverBundle(
            String aClaspLibraryPath,
            String aUBCSATLibraryPath,
            IStationManager aStationManager,
            IConstraintManager aConstraintManager,
            String aResultFile,
            final boolean presolve,
            final boolean decompose,
            final boolean underconstrained,
            final String serverURL,
            int numCores,
            final boolean cacheResults
    ) {
        super(aStationManager, aConstraintManager);
        log.info("Initializing solver with the following solver options: presolve {}, decompose {}, underconstrained {}, serverURL {}", presolve, decompose, underconstrained, serverURL);
        final SATCompressor aCompressor = new SATCompressor(this.getConstraintManager());
        final UBCSATISolverFactory ubcsatiSolverFactory = new UBCSATISolverFactory(new UBCSATLibraryGenerator(aUBCSATLibraryPath), aCompressor, getConstraintManager());
        solver = ubcsatiSolverFactory.create(UBCSATLibSATSolverParameters.DEFAULT_DCCA);
        solver = new AssignmentVerifierDecorator(solver, getConstraintManager());
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
