package ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles;

import ca.ubc.cs.beta.stationpacking.facade.datamanager.data.ManagerBundle;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.execution.parameters.smac.SATFCHydraParams;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories.Clasp3ISolverFactory;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories.Clasp3LibraryGenerator;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories.UBCSATISolverFactory;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories.UBCSATLibraryGenerator;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.VoidSolver;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.ConstraintGraphNeighborhoodPresolver;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.StationSubsetSATCertifier;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.StationSubsetUNSATCertifier;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.strategies.*;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.ConstraintGrouper;
import ca.ubc.cs.beta.stationpacking.solvers.composites.ISolverFactory;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.CPUTimeDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.ConnectedComponentGroupingDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.UnderconstrainedStationRemoverSolverDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.consistency.ArcConsistencyEnforcerDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.SATCompressor;
import ca.ubc.cs.beta.stationpacking.solvers.underconstrained.HeuristicUnderconstrainedStationFinder;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by newmanne on 11/06/15.
 */
@Slf4j
public class SATFCHydraBundle extends ASolverBundle {

    private final UBCSATLibraryGenerator ubcsatLibraryGenerator;
    ISolver fSolver;
    private final Clasp3LibraryGenerator claspLibraryGenerator;

    public SATFCHydraBundle(ManagerBundle dataBundle, SATFCHydraParams params, String aClaspLibraryPath) {
        super(dataBundle);

        IConstraintManager aConstraintManager = dataBundle.getConstraintManager();
        params.claspConfig = params.claspConfig.replaceAll("_SPACE_", " ");
        params.ubcsatConfig = params.claspConfig.replaceAll("_SPACE_", " ");

        final SATCompressor aCompressor = new SATCompressor(this.getConstraintManager());
        claspLibraryGenerator = new Clasp3LibraryGenerator(aClaspLibraryPath);
        ubcsatLibraryGenerator = new UBCSATLibraryGenerator(aUBCSATLibraryPath);
        final Clasp3ISolverFactory clasp3ISolverFactory = new Clasp3ISolverFactory(claspLibraryGenerator, aCompressor, getConstraintManager());
        final UBCSATISolverFactory ubcsatiSolverFactory = new UBCSATISolverFactory(ubcsatLibraryGenerator, aCompressor, getConstraintManager());
        Map<YAMLBundle.SolverType, ISolverFactory> solverTypeToFactory = new HashMap<>();
        solverTypeToFactory.put(YAMLBundle.SolverType.CONNECTED_COMPONENTS, solver -> {
            return new ConnectedComponentGroupingDecorator(solver, new ConstraintGrouper(), aConstraintManager);
        });
        solverTypeToFactory.put(YAMLBundle.SolverType.ARC_CONSISTENCY, solver -> {
            return new ArcConsistencyEnforcerDecorator(solver, getConstraintManager());
        });
        solverTypeToFactory.put(YAMLBundle.SolverType.UNDERCONSTRAINED, solver -> {
            return new UnderconstrainedStationRemoverSolverDecorator(solver, getConstraintManager(), new HeuristicUnderconstrainedStationFinder(getConstraintManager(), true), true);
        });
        solverTypeToFactory.put(YAMLBundle.SolverType.CLASP, solver -> {
            return clasp3ISolverFactory.create(params.claspConfig);
        });
        solverTypeToFactory.put(YAMLBundle.SolverType.UBCSAT, solver -> {
            return ubcsatiSolverFactory.create(params.ubcsatConfig);
        });
        solverTypeToFactory.put(YAMLBundle.SolverType.UNSAT_PRESOLVER, solver -> {
            final IStationAddingStrategy stationAddingStrategy;
            switch (params.presolverExpansionMethod) {
                case NEIGHBOURHOOD:
                    stationAddingStrategy = new AddNeighbourLayerStrategy();
                    break;
                case UNIFORM_RANDOM:
                    stationAddingStrategy = new AddRandomNeighboursStrategy(params.presolverNumNeighbours, 1);
                    break;
                default:
                    throw new IllegalStateException("Unrecognized presolver expansion method " + params.presolverExpansionMethod);
            }
            final IStationPackingConfigurationStrategy stationPackingConfigurationStrategy = params.presolverIterativelyDeepen ? new IterativeDeepeningConfigurationStrategy(stationAddingStrategy, params.presolverBaseCutoff, params.presolverScaleFactor) : new IterativeDeepeningConfigurationStrategy(stationAddingStrategy, params.presolverCutoff);
            return new ConstraintGraphNeighborhoodPresolver(solver,
                                    new StationSubsetUNSATCertifier(clasp3ISolverFactory.create(params.claspConfig)),
                                    stationPackingConfigurationStrategy,
                    getConstraintManager());
        });
        solverTypeToFactory.put(YAMLBundle.SolverType.SAT_PRESOLVER, solver -> {
            final IStationAddingStrategy stationAddingStrategy;
            switch (params.presolverExpansionMethod) {
                case NEIGHBOURHOOD:
                    stationAddingStrategy = new AddNeighbourLayerStrategy();
                    break;
                case UNIFORM_RANDOM:
                    stationAddingStrategy = new AddRandomNeighboursStrategy(params.presolverNumNeighbours, 1);
                    break;
                default:
                    throw new IllegalStateException("Unrecognized presolver expansion method " + params.presolverExpansionMethod);
            }
            final IStationPackingConfigurationStrategy stationPackingConfigurationStrategy = params.presolverIterativelyDeepen ? new IterativeDeepeningConfigurationStrategy(stationAddingStrategy, params.presolverBaseCutoff, params.presolverScaleFactor) : new IterativeDeepeningConfigurationStrategy(stationAddingStrategy, params.presolverCutoff);
            return new ConstraintGraphNeighborhoodPresolver(solver,
                                new StationSubsetSATCertifier(clasp3ISolverFactory.create(params.claspConfig)),
                                    stationPackingConfigurationStrategy,
                    getConstraintManager());
        });
        fSolver = new VoidSolver();
        fSolver = new CPUTimeDecorator(fSolver, claspLibraryGenerator.createLibrary());
        log.debug(params.getSolverOrder().toString());
        for (YAMLBundle.SolverType solverType : params.getSolverOrder()) {
            if (solverType != YAMLBundle.SolverType.NONE) {
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
        ubcsatLibraryGenerator.notifyShutdown();
    }

}
