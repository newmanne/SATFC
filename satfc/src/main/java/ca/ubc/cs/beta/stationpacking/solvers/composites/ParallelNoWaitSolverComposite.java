package ca.ubc.cs.beta.stationpacking.solvers.composites;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.solvers.termination.InterruptibleTerminationCriterion;
import ca.ubc.cs.beta.stationpacking.utils.Watch;

import com.google.common.collect.Queues;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

/**
 * Created by newmanne on 26/05/15.
 */
@Slf4j
/**
 * This class executes the ISolver's created from the ISolverFactories that are passed into its constructor in parallel.
 * A result is returned **immediately** after a conclusive result is found, even though other threads may still be computing the (now stale) result.
 */
public class ParallelNoWaitSolverComposite implements ISolver {

    private final ListeningExecutorService executorService;
    private final List<BlockingQueue<ISolver>> listOfSolverQueues;

    /**
     * @param threadPoolSize The number of threads to use in the thread pool
     * @param solvers        A list of ISolverFactory, sorted by priority (first in the list means high priority). This is the order that we will try things in if there are not enough threads to go around
     */
    public ParallelNoWaitSolverComposite(int threadPoolSize, List<ISolverFactory> solvers) {
        log.debug("Creating a fixed pool with {} threads", threadPoolSize);
        executorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(threadPoolSize));
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

    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
        if (aTerminationCriterion.hasToStop()) {
            return SolverResult.createTimeoutResult(0);
        }
        log.debug("Solving via parallel solver");
        final Watch watch = Watch.constructAutoStartWatch();
        // Swap out the termination criterion to one that can be interrupted
        final ITerminationCriterion.IInterruptibleTerminationCriterion interruptibleCriterion = new InterruptibleTerminationCriterion(aTerminationCriterion);
        final Lock lock = new ReentrantLock();
        final Condition notFinished = lock.newCondition();
        // You should only modify this variable while holding the lock
        final Wrapper<SolverResult> resultWrapper = new Wrapper<>();
        // We maintain a list of all the solvers current solving the problem so we know who to interrupt via the interrupt method
        final List<ISolver> solversSolvingCurrentProblem = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger counter = new AtomicInteger(0);
        final List<Future> futures = new ArrayList<>();
        try {
            lock.lock();
            // Submit one job per each solver in the portfolio
            listOfSolverQueues.forEach(solverQueue -> {
                final ListenableFuture<Void> future = executorService.submit(() -> {
                    log.debug("Job starting...");
                    if (!interruptibleCriterion.hasToStop()) {
                        final ISolver solver = solverQueue.poll();
                        if (solver == null) {
                            throw new IllegalStateException("Couldn't take a solver from the queue!");
                        }
                        // During this block (while you are added to this list) it is safe for you to be interrupted via the interrupt method
                        solversSolvingCurrentProblem.add(solver);
                        log.debug("Begin solve {}", solver.getClass().getSimpleName());
                        final SolverResult solverResult = solver.solve(aInstance, interruptibleCriterion, aSeed);
                        log.debug("End solve {}", solver.getClass().getSimpleName());
                        solversSolvingCurrentProblem.remove(solver);
                        // Interrupt only if the result is conclusive. Only the first one will go through this block
                        if (solverResult.isConclusive() && interruptibleCriterion.interrupt()) {
                            log.debug("Found a conclusive result, interrupting other concurrent solvers");
                            synchronized (solversSolvingCurrentProblem) {
                                solversSolvingCurrentProblem.forEach(ISolver::interrupt);
                            }
                            // Signal the initial thread that it can move forwards
                            lock.lock();
                            log.debug("Signalling the blocked thread to wake up!");
                            notFinished.signal();
                            resultWrapper.setWrapped(solverResult);
                            lock.unlock();
                        }
                        // Return your solver back to the queue
                        if (!solverQueue.offer(solver)) {
                            throw new IllegalStateException("Wasn't able to return solver to the queue!");
                        }
                    }
                    if (counter.incrementAndGet() == listOfSolverQueues.size()) {
                        /**
                         * If we get inside this block, then every thread has had a crack at this problem.
                         * Because it's possible that every single result was inconclusive, we signal
                         * It's hopefully a lost signal and the blocking thread is long gone, but just in case it's time to wakeup and report timeout
                         */
                        log.debug("I'm the last one here, so I'll signal the main thread to wake up (just incase)!");
                        lock.lock();
                        notFinished.signal();
                        lock.unlock();
                    }
                    log.debug("Job ending...");
                    return null;
                });
                futures.add(future);
                addExceptionHandlingCallback(future);
            });
            // Wait for a thread to complete solving and signal you, or all threads to timeout
            log.debug("Main thread going to sleep");
            notFinished.await();
            log.debug("Main thread waking up, cancelling futures");
            // Might as well cancel any jobs that haven't run yet. We don't interrupt them (via Thread interrupt) if they have already started, because we have our own interrupt system
            futures.forEach(future -> future.cancel(false));
            log.debug("Returning now");
            return resultWrapper.getWrapped() == null ? SolverResult.createTimeoutResult(watch.getElapsedTime()) : new SolverResult(resultWrapper.getWrapped().getResult(), watch.getElapsedTime(), resultWrapper.getWrapped().getAssignment());
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while running parallel job", e);
        } finally {
            lock.unlock();
        }
    }

    private void addExceptionHandlingCallback(ListenableFuture<Void> future) {
        Futures.addCallback(future, new FutureCallback<Void>() {
            @Override
            public void onSuccess(Void result) {

            }

            @Override
            public void onFailure(Throwable t) {
                if (t instanceof CancellationException) {
                    return;
                }
                throw new RuntimeException("Error executing task!", t);
            }
        });
    }

    @Override
    public void notifyShutdown() {
        executorService.shutdown();
        listOfSolverQueues.forEach(queue -> queue.forEach(ISolver::notifyShutdown));
    }

    @Override
    public void interrupt() {
        throw new RuntimeException("Not yet implemented");
    }

    @NoArgsConstructor
    public static class Wrapper<T> {
        @Getter
        T wrapped;

        public synchronized void setWrapped(T t) {
            if (wrapped != null) {
                throw new IllegalStateException("Object is already set!");
            }
            wrapped = t;
        }
    }


}