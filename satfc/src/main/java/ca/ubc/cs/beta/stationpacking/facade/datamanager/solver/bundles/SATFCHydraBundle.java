package ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.execution.parameters.smac.SATFCHydraParams;
import ca.ubc.cs.beta.stationpacking.facade.SATFCFacadeParameter;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.data.ManagerBundle;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories.Clasp3ISolverFactory;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories.Clasp3LibraryGenerator;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories.ISATSolverFactory;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories.UBCSATISolverFactory;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories.UBCSATLibraryGenerator;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.VoidSolver;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.ConstraintGraphNeighborhoodPresolver;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.StationSubsetSATCertifier;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.StationSubsetUNSATCertifier;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.strategies.AddNeighbourLayerStrategy;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.strategies.AddRandomNeighboursStrategy;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.strategies.IStationAddingStrategy;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.strategies.IStationPackingConfigurationStrategy;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.strategies.IterativeDeepeningConfigurationStrategy;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.ConstraintGrouper;
import ca.ubc.cs.beta.stationpacking.solvers.composites.ISolverFactory;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.AssignmentVerifierDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.CPUTimeDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.ConnectedComponentGroupingDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.UnderconstrainedStationRemoverSolverDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.consistency.ArcConsistencyEnforcerDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.SATCompressor;
import ca.ubc.cs.beta.stationpacking.solvers.termination.interrupt.IPollingService;
import ca.ubc.cs.beta.stationpacking.solvers.underconstrained.HeuristicUnderconstrainedStationFinder;

import com.google.common.base.Preconditions;

/**
 * Created by newmanne on 11/06/15.
 */
@Slf4j
public class SATFCHydraBundle extends ASolverBundle {

    private final UBCSATLibraryGenerator ubcsatLibraryGenerator;
    ISolver fSolver;
    private final Clasp3LibraryGenerator claspLibraryGenerator;

    public SATFCHydraBundle(ManagerBundle dataBundle, SATFCFacadeParameter parameters) {
        super(dataBundle);

        final SATFCHydraParams params = parameters.getHydraParams();
        final IPollingService pollingService = parameters.getPollingService();
        
        IConstraintManager aConstraintManager = dataBundle.getConstraintManager();
        params.claspConfig = params.claspConfig.replaceAll("_SPACE_", " ");
        params.ubcsatConfig = params.ubcsatConfig.replaceAll("_SPACE_", " ");

        claspLibraryGenerator = new Clasp3LibraryGenerator(parameters.getClaspLibrary());
        ubcsatLibraryGenerator = new UBCSATLibraryGenerator(parameters.getUbcsatLibrary());

        final SATCompressor aCompressor = new SATCompressor(dataBundle.getConstraintManager(), params.encodingType);
        final ISATSolverFactory satSolverFactory;
        final String satSolverParams;
        if (params.solverChoice.equals(SATFCHydraParams.SatSolverChoice.CLASP)) {
            satSolverFactory = new Clasp3ISolverFactory(claspLibraryGenerator, aCompressor, pollingService);
            satSolverParams = params.claspConfig;
        } else {
            Preconditions.checkState(params.solverChoice.equals(SATFCHydraParams.SatSolverChoice.UBCSAT));
            satSolverFactory = new UBCSATISolverFactory(ubcsatLibraryGenerator, aCompressor, pollingService);
            satSolverParams = params.ubcsatConfig;
        }
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
            return satSolverFactory.create(satSolverParams);
        });
        solverTypeToFactory.put(YAMLBundle.SolverType.UBCSAT, solver -> {
            return satSolverFactory.create(satSolverParams);
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
                                    new StationSubsetUNSATCertifier(satSolverFactory.create(satSolverParams)),
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
                    stationAddingStrategy = new AddRandomNeighboursStrategy(params.presolverNumNeighbours);
                    break;
                default:
                    throw new IllegalStateException("Unrecognized presolver expansion method " + params.presolverExpansionMethod);
            }
            final IStationPackingConfigurationStrategy stationPackingConfigurationStrategy = params.presolverIterativelyDeepen ? new IterativeDeepeningConfigurationStrategy(stationAddingStrategy, params.presolverBaseCutoff, params.presolverScaleFactor) : new IterativeDeepeningConfigurationStrategy(stationAddingStrategy, params.presolverCutoff);
            return new ConstraintGraphNeighborhoodPresolver(solver,
                                new StationSubsetSATCertifier(satSolverFactory.create(satSolverParams)),
                                    stationPackingConfigurationStrategy,
                    getConstraintManager());
        });
        log.debug(params.getSolverOrder().toString());
        fSolver = new VoidSolver();
        for (YAMLBundle.SolverType solverType : params.getSolverOrder()) {
            if (!solverType.equals(YAMLBundle.SolverType.NONE)) {
                fSolver = solverTypeToFactory.get(solverType).extend(fSolver);
            }
        }
        fSolver = new CPUTimeDecorator(fSolver, claspLibraryGenerator.createLibrary());
        fSolver = new AssignmentVerifierDecorator(fSolver, aConstraintManager, dataBundle.getStationManager());
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
