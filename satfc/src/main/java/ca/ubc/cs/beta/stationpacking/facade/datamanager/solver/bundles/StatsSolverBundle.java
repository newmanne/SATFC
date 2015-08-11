package ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.VoidSolver;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.AssignmentVerifierDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.UnderconstrainedStationRemoverSolverDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.consistency.ArcConsistencyEnforcerDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.underconstrained.HeuristicUnderconstrainedStationFinder;
import ca.ubc.cs.beta.stationpacking.solvers.underconstrained.IUnderconstrainedStationFinder;
import ca.ubc.cs.beta.stationpacking.solvers.underconstrained.UnderconstrainedStationFinder;

/**
 * Created by newmanne on 2015-07-12.
 */
public class StatsSolverBundle extends ASolverBundle {

    ISolver solver;

    public StatsSolverBundle(
            IStationManager aStationManager,
            IConstraintManager aConstraintManager,
            String aClaspLibraryPath
            ) {
        super(aStationManager, aConstraintManager);
        solver = new VoidSolver();
//        final IUnderconstrainedStationFinder finder = new HeuristicUnderconstrainedStationFinder(getConstraintManager(), true);
        final IUnderconstrainedStationFinder finder = new UnderconstrainedStationFinder(getConstraintManager(), true);
        solver = new UnderconstrainedStationRemoverSolverDecorator(solver, getConstraintManager(), finder, true);
        solver = new ArcConsistencyEnforcerDecorator(solver, getConstraintManager());
        solver = new AssignmentVerifierDecorator(solver, getConstraintManager());
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



