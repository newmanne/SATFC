package ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.ConstraintGraphNeighborhoodPresolver;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.StationSubsetSATCertifier;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.ConstraintGrouper;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.IComponentGrouper;
import ca.ubc.cs.beta.stationpacking.solvers.composites.SequentialSolversComposite;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.CNFSaverSolverDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.ConnectedComponentGroupingDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.solvers.termination.cputime.CPUTimeTerminationCriterionFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by newmanne on 24/04/15.
 */
public class CNFSolverBundle extends ASolverBundle {

    private ISolver cnfOnlySolver;
    public static Map<String, String> decompositionHashes = new HashMap<>();
    // TODO: need this in the CNFSaver SATFCFacadeExecutor.decompositionHashes.put(aInstance.getName(), aCNFFilename); - probably with event bus

    public CNFSolverBundle(
            IStationManager aStationManager,
            IConstraintManager aConstraintManager,
            String aCNFDirectory
    ) {
        super(aStationManager, aConstraintManager);
        cnfOnlySolver = new VoidSolver();
        // save the cnf for each component
        cnfOnlySolver = new CNFSaverSolverDecorator(cnfOnlySolver, getConstraintManager(), aCNFDirectory);
        IComponentGrouper aGrouper = new ConstraintGrouper();
        cnfOnlySolver = new ConnectedComponentGroupingDecorator(cnfOnlySolver, aGrouper, getConstraintManager(), true);

        cnfOnlySolver = new SequentialSolversComposite(
                Arrays.asList(
                        new ConstraintGraphNeighborhoodPresolver(aConstraintManager,
                                Arrays.asList(
                                        new StationSubsetSATCertifier(new CNFSaverSolverDecorator(new VoidSolver(), aConstraintManager, aCNFDirectory + "_PRESAT"), new CPUTimeTerminationCriterionFactory(60.0))
                                )),
                        cnfOnlySolver
                )
        );

        // save the cnf for the full problem
        cnfOnlySolver = new CNFSaverSolverDecorator(cnfOnlySolver,getConstraintManager(),aCNFDirectory);
    }

    public static class VoidSolver implements ISolver {
        @Override
        public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
            return new SolverResult(SATResult.TIMEOUT, 0.0);
        }

        @Override
        public void interrupt() throws UnsupportedOperationException {

        }

        @Override
        public void notifyShutdown() {

        }
    }

    @Override
    public ISolver getSolver(StationPackingInstance aInstance) {
        return cnfOnlySolver;
    }

    @Override
    public void close() throws Exception {
        cnfOnlySolver.notifyShutdown();
    }

}
