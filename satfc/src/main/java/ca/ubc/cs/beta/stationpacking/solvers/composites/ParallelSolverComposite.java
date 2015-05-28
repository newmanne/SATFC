package ca.ubc.cs.beta.stationpacking.solvers.composites;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

import lombok.extern.slf4j.Slf4j;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.solvers.termination.InterruptibleTerminationCriterion;
import ca.ubc.cs.beta.stationpacking.utils.Watch;

/**
 * Created by newmanne on 13/05/15.
 * A very simple parallel solver that forks and joins (i.e. blocks until every solver completes (though it does try to interrupt them))
 */
@Slf4j
public class ParallelSolverComposite implements ISolver {

    Collection<ISolver> solvers;
    private final ForkJoinPool forkJoinPool;

    public ParallelSolverComposite(int threadPoolSize, List<ISolver> solvers) {
        this.solvers = new ArrayList<>(solvers);
        log.debug("Creating a fork join pool with {} threads", threadPoolSize);
        forkJoinPool = new ForkJoinPool(threadPoolSize);
    }

    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
        Watch watch = Watch.constructAutoStartWatch();
        // Swap out the termination criterion to one that can be interrupted
        final ITerminationCriterion.IInterruptibleTerminationCriterion interruptibleCriterion = new InterruptibleTerminationCriterion(aTerminationCriterion);
        try {
            final SolverResult endResult = forkJoinPool.submit(() -> {
                return solvers.parallelStream()
                        .map(solver -> {
                            final SolverResult solve = solver.solve(aInstance, interruptibleCriterion, aSeed);
                            log.trace("Returned from solver");
                            // Interrupt only if the result is conclusive
                            if (solve.getResult().isConclusive() && interruptibleCriterion.interrupt()) {
                                log.debug("Found a conclusive result {}, interrupting other concurrent solvers", solve);
                            }
                            return solve;
                        })
                        .filter(result -> result.getResult().isConclusive())
                        .findAny();
            }).get().orElse(new SolverResult(SATResult.TIMEOUT, watch.getElapsedTime()));
            return new SolverResult(endResult.getResult(), watch.getElapsedTime(), endResult.getAssignment());
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error processing jobs in parallel!", e);
        }
    }

    @Override
    public void notifyShutdown() {
        solvers.forEach(ISolver::notifyShutdown);
    }

	@Override
	public void interrupt() {
		throw new RuntimeException("Interrupt not yet implemeneted...");
	}

}
