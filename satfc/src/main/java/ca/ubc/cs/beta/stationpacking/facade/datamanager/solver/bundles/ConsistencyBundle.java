package ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.execution.parameters.solver.sat.ClaspLibSATSolverParameters;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories.Clasp3ISolverFactory;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.VoidSolver;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.ArcConsistencyEnforcerDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.SATCompressor;

/**
 * Created by newmanne on 05/06/15.
 */
public class ConsistencyBundle extends ASolverBundle {

    ISolver solver;

    public ConsistencyBundle(IStationManager aStationManager, IConstraintManager aConstraintManager, String aClaspLibraryPath) {
        super(aStationManager, aConstraintManager);
        final SATCompressor aCompressor = new SATCompressor(this.getConstraintManager());
        final Clasp3ISolverFactory clasp3ISolverFactory = new Clasp3ISolverFactory(new ClaspLibraryGenerator(aClaspLibraryPath), aCompressor, getConstraintManager());
        solver = clasp3ISolverFactory.create(ClaspLibSATSolverParameters.UHF_CONFIG_04_15_h1);
        solver = new ArcConsistencyEnforcerDecorator(solver, aConstraintManager);
    }

    @Override
    public ISolver getSolver(StationPackingInstance aInstance) {
        return solver;
    }

    @Override
    public void close() throws Exception {

    }
}
