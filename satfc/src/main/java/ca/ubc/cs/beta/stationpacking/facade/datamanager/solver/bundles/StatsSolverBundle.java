package ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.execution.parameters.solver.sat.ClaspLibSATSolverParameters;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.data.ManagerBundle;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories.Clasp3ISolverFactory;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories.ClaspLibraryGenerator;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories.PythonInterpreterFactory;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.VoidSolver;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.AssignmentVerifierDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.PythonAssignmentVerifierDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.UnderconstrainedStationRemoverSolverDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.consistency.ArcConsistencyEnforcerDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.consistency.ChannelKillerDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.SATCompressor;
import ca.ubc.cs.beta.stationpacking.solvers.underconstrained.HeuristicUnderconstrainedStationFinder;
import ca.ubc.cs.beta.stationpacking.solvers.underconstrained.IUnderconstrainedStationFinder;

/**
 * Created by newmanne on 2015-07-12.
 */
public class StatsSolverBundle extends ASolverBundle {

    ISolver solver;

    public StatsSolverBundle(
            ManagerBundle dataBundle,
            String aClaspLibraryPath
            ) {
        super(dataBundle);

        final SATCompressor aCompressor = new SATCompressor(this.getConstraintManager());
        final Clasp3ISolverFactory clasp3ISolverFactory = new Clasp3ISolverFactory(new ClaspLibraryGenerator(aClaspLibraryPath), aCompressor, getConstraintManager());
        final PythonInterpreterFactory python = new PythonInterpreterFactory(getInterferenceFolder(), getCompact());

        solver = new VoidSolver();
        final IUnderconstrainedStationFinder heuristicFinder = new HeuristicUnderconstrainedStationFinder(getConstraintManager(), true);
//        final IUnderconstrainedStationFinder lpFinder = new MIPUnderconstrainedStationFinder(getConstraintManager(), true);
        solver = new UnderconstrainedStationRemoverSolverDecorator(solver, getConstraintManager(), heuristicFinder, true);
        solver = new ChannelKillerDecorator(solver, clasp3ISolverFactory.create(ClaspLibSATSolverParameters.UHF_CONFIG_04_15_h1), getConstraintManager());
        solver = new ArcConsistencyEnforcerDecorator(solver, getConstraintManager());
        solver = new PythonAssignmentVerifierDecorator(solver, python);
        solver = new AssignmentVerifierDecorator(solver, getConstraintManager(), getStationManager());
    }

    @Override
    public void close() throws Exception {
        solver.notifyShutdown();
    }

    @Override
    public ISolver getSolver(StationPackingInstance aInstance) {
        return solver;
    }

}



