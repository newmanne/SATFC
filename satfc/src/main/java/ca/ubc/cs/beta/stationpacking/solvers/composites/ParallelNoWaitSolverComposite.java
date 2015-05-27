package ca.ubc.cs.beta.stationpacking.solvers.composites;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.solvers.termination.InterruptibleTerminationCriterion;
import ca.ubc.cs.beta.stationpacking.utils.Watch;
import com.google.common.collect.Queues;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by newmanne on 26/05/15.
 */
@Slf4j
/**
 * This class executes the ISolver's created from the ISolverFactories that are passed into its constructor in parallel.
 * A result is returned **immediately** after a conclusive result is found, even though other threads may still be computing the (now stale) result.
 */
public class ParallelNoWaitSolverComposite implements ISolver {

    private final ForkJoinPool forkJoinPool;
    private final List<BlockingQueue<ISolver>> listOfSolverQueues;

    /**
     *
     * @param threadPoolSize
     * @param solvers A list of ISolverFactory, sorted by priority (first in the list means high priority). This is the order that we will try things in if there are not enough threads to go around
     */
    public ParallelNoWaitSolverComposite(int threadPoolSize, List<ISolverFactory> solvers) {
        log.info("Creating a fork join pool with {} threads", threadPoolSize);
        forkJoinPool = new ForkJoinPool(threadPoolSize);
        listOfSolverQueues = new ArrayList<>(solvers.size());
        for (ISolverFactory solverFactory : solvers) {
            final LinkedBlockingQueue<ISolver> solverQueue = Queues.newLinkedBlockingQueue(threadPoolSize);
            listOfSolverQueues.add(solverQueue);
            for (int i = 0; i < threadPoolSize; i++) {
                final ISolver solver = solverFactory.create();
                solverQueue.offer(solver);
            }
        }
    }

    public ParallelNoWaitSolverComposite(List<ISolverFactory> solvers) {
        this(Runtime.getRuntime().availableProcessors(), solvers);
    }

    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
        log.debug("Solving via parallel solver");
        final Watch watch = Watch.constructAutoStartWatch();
        // Swap out the termination criterion to one that can be interrupted
        final ITerminationCriterion.IInterruptibleTerminationCriterion interruptibleCriterion = new InterruptibleTerminationCriterion(aTerminationCriterion);
        final Lock lock = new ReentrantLock();
        final Condition notFinished = lock.newCondition();
        // You should only modify this variable while holding the lock
        final SolverResultParallelWrapper resultWrapper = new SolverResultParallelWrapper();
        // We maintain a list of all the solvers current solving the problem so we know who to interrupt
        final List<ISolver> solversSolvingCurrentProblem = Collections.synchronizedList(new ArrayList<>());
        try {
            lock.lock();
            log.debug("Submitting new task to the pool");
            forkJoinPool.submit(() -> {
                listOfSolverQueues.parallelStream()
                    .forEach(solverQueue -> {
                        if (!interruptibleCriterion.hasToStop()) {
                            final ISolver solver = solverQueue.poll();
                            if (solver == null) {
                                throw new IllegalStateException("Couldn't take a solver from the queue!");
                            }
                            solversSolvingCurrentProblem.add(solver);
                            final SolverResult solverResult = solver.solve(aInstance, interruptibleCriterion, aSeed);
                            // Interrupt only if the result is conclusive. Only the first one will go through this block
                            if (solverResult.getResult().isConclusive() && interruptibleCriterion.interrupt()) {
                                solversSolvingCurrentProblem.forEach(ISolver::interrupt);
                                log.debug("Found a conclusive result, interrupting other concurrent solvers");
                                // Signal the initial thread that it can move forwards
                                lock.lock();
                                notFinished.signal();
                                resultWrapper.setResult(solverResult);
                                lock.unlock();
                            }
                            // Return your solver back to the queue
                            solverQueue.offer(solver);
                        }
                        log.trace("Returning to the pool...");
                    });
                // If we get here, every thread is finished. Because there might have been a timeout, we signal the blocking thread
                // PROBLEM: You can't reclaim ANY threads! until here?
                lock.lock();
                notFinished.signal();
                lock.unlock();
            });
            notFinished.await();
            return resultWrapper.getResult() == null ? SolverResult.createTimeoutResult(watch.getElapsedTime()) : new SolverResult(resultWrapper.getResult().getResult(), watch.getElapsedTime(), resultWrapper.getResult().getAssignment());
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while running parallel job", e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void notifyShutdown() {
        listOfSolverQueues.forEach(queue -> queue.forEach(ISolver::notifyShutdown));
    }

    @NoArgsConstructor
    public static class SolverResultParallelWrapper {
        @Setter
        @Getter
        SolverResult result;
    }

}