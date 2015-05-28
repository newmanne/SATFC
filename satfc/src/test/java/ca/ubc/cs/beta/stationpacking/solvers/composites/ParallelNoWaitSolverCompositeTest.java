package ca.ubc.cs.beta.stationpacking.solvers.composites;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import ca.ubc.cs.beta.stationpacking.StationPackingTestUtils;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.termination.cputime.CPUTimeTerminationCriterion;
import ca.ubc.cs.beta.stationpacking.utils.Watch;

@Slf4j
public class ParallelNoWaitSolverCompositeTest {

    /**
     * Run a lot of infinite loop solvers and one auto-return the answer solver
     * The good solver should interrupt the infinite loopers
     */
    @Test(timeout = 3000)
    public void testInterruptOtherSolvers() {
        final List<ISolverFactory> solvers = new ArrayList<>();
        // We need to have enough threads so that the good solver is run concurrently with the bad solvers
        final int nThreads = 11;
        for (int i = 0; i < nThreads - 1; i++) {
            // create many solvers that loop infinitely
            solvers.add(()->(aInstance, aTerminationCriterion, aSeed) -> {
                final Watch watch = Watch.constructAutoStartWatch();
                while (!aTerminationCriterion.hasToStop()) {} // infinite loop
                return new SolverResult(SATResult.TIMEOUT, watch.getElapsedTime());
            });
        }
        // construct a solver that just returns the answer immediately
        solvers.add(()->(aInstance, aTerminationCriterion, aSeed) -> new SolverResult(SATResult.SAT, 32.0, StationPackingTestUtils.getSimpleInstanceAnswer()));
        final ParallelNoWaitSolverComposite parallelSolverComposite = new ParallelNoWaitSolverComposite(nThreads, solvers);
        final SolverResult solve = parallelSolverComposite.solve(StationPackingTestUtils.getSimpleInstance(), new CPUTimeTerminationCriterion(60), 1);
        assertEquals(solve.getResult(), SATResult.SAT);
    }

    @Test
    public void everySolverIsQueriedIfNoOneHasAConclusiveAnswer() {
        final List<ISolverFactory> solvers = new ArrayList<>();
        final AtomicInteger numCalls = new AtomicInteger(0);
        final int N_SOLVERS = 10;
        for (int i = 0; i < N_SOLVERS; i++) {
            solvers.add(()->(aInstance, aTerminationCriterion, aSeed) -> {
                numCalls.incrementAndGet();
                return new SolverResult(SATResult.TIMEOUT, 60.0);
            });
        }
        final ParallelNoWaitSolverComposite parallelSolverComposite = new ParallelNoWaitSolverComposite(1, solvers);
        final SolverResult solve = parallelSolverComposite.solve(StationPackingTestUtils.getSimpleInstance(), new CPUTimeTerminationCriterion(60), 1);
        assertEquals(numCalls.get(), N_SOLVERS); // every solver should be asked
        assertEquals(solve.getResult(), SATResult.TIMEOUT);
    }

    @Test(timeout = 2000)
    public void notEverySolverIsWaitedForIfOneHasAConclusiveAnswer() {
        final List<ISolverFactory> solvers = new ArrayList<>();
        solvers.add(()->(aInstance, aTerminationCriterion, aSeed) -> new SolverResult(SATResult.SAT, 1.0, ImmutableMap.of()));
        solvers.add(()->(aInstance, aTerminationCriterion, aSeed) -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {
            }
            return new SolverResult(SATResult.TIMEOUT, 1.0);
        });
        final ParallelNoWaitSolverComposite parallelSolverComposite = new ParallelNoWaitSolverComposite(1, solvers);
        final SolverResult solve = parallelSolverComposite.solve(StationPackingTestUtils.getSimpleInstance(), new CPUTimeTerminationCriterion(60), 1);
        assertEquals(solve.getResult(), SATResult.SAT);
    }

}