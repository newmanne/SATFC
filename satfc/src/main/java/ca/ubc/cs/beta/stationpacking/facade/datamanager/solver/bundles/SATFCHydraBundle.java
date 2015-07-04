package ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.execution.parameters.smac.SATFCHydraParams;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories.Clasp3ISolverFactory;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.VoidSolver;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.ConstraintGraphNeighborhoodPresolver;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.StationSubsetSATCertifier;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.ConstraintGrouper;
import ca.ubc.cs.beta.stationpacking.solvers.composites.ISolverFactory;
import ca.ubc.cs.beta.stationpacking.solvers.composites.SequentialSolversComposite;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.ConnectedComponentGroupingDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.UnderconstrainedStationRemoverSolverDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.consistency.ArcConsistencyEnforcerDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.SATCompressor;
import ca.ubc.cs.beta.stationpacking.solvers.termination.cputime.CPUTimeTerminationCriterionFactory;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by newmanne on 11/06/15.
 */
@Slf4j
public class SATFCHydraBundle extends ASolverBundle {

    ISolver fSolver;
    private final ClaspLibraryGenerator claspLibraryGenerator;

    public SATFCHydraBundle(IStationManager aStationManager, IConstraintManager aConstraintManager, SATFCHydraParams params, String aClaspLibraryPath) {
        super(aStationManager, aConstraintManager);
        final SATCompressor aCompressor = new SATCompressor(this.getConstraintManager());
        claspLibraryGenerator = new ClaspLibraryGenerator(aClaspLibraryPath);
        final Clasp3ISolverFactory clasp3ISolverFactory = new Clasp3ISolverFactory(claspLibraryGenerator, aCompressor, getConstraintManager());
        Map<SATFCHydraParams.SolverType, ISolverFactory> solverTypeToFactory = new HashMap<>();
        solverTypeToFactory.put(SATFCHydraParams.SolverType.CONNECTED_COMPONENTS, solver -> {
            return new ConnectedComponentGroupingDecorator(solver, new ConstraintGrouper(), aConstraintManager);
        });
        solverTypeToFactory.put(SATFCHydraParams.SolverType.ARC_CONSISTENCY, solver -> {
            return new ArcConsistencyEnforcerDecorator(solver, getConstraintManager());
        });
        solverTypeToFactory.put(SATFCHydraParams.SolverType.UNDERCONSTRAINED, solver -> {
            return new UnderconstrainedStationRemoverSolverDecorator(solver, getConstraintManager());
        });
        solverTypeToFactory.put(SATFCHydraParams.SolverType.CLASP, solver -> {
            return clasp3ISolverFactory.create(params.claspConfig);
        });
        solverTypeToFactory.put(SATFCHydraParams.SolverType.PRESOLVER, solver -> {
            return new SequentialSolversComposite(
                    Arrays.asList(
                            new ConstraintGraphNeighborhoodPresolver(
                                    aConstraintManager,
                                    Arrays.asList(
                                            new StationSubsetSATCertifier(
                                                    clasp3ISolverFactory.create(params.claspConfig),
                                                    new CPUTimeTerminationCriterionFactory(params.presolverCutoff)
                                            )
                                    )
                            ),
                            solver));
        });
        fSolver = new VoidSolver();
        log.info(params.getSolverOrder().toString());
        for (SATFCHydraParams.SolverType solverType : params.getSolverOrder()) {
            if (solverType != SATFCHydraParams.SolverType.NONE) {
                fSolver = solverTypeToFactory.get(solverType).extend(fSolver);
            }
        }
    }

    @Override
    public ISolver getSolver(StationPackingInstance aInstance) {
        return fSolver;
    }

    @Override
    public void close() throws Exception {
        fSolver.notifyShutdown();
        claspLibraryGenerator.notifyShutdown();
    }
}
