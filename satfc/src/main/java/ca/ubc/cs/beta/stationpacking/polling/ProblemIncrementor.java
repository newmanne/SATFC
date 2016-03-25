package ca.ubc.cs.beta.stationpacking.polling;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import ca.ubc.cs.beta.stationpacking.solvers.decorators.ISATFCInterruptible;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
* Created by newmanne on 15/10/15.
* Some SATFC methods delve into long blocks of code that will not periodically check for interrupt signals (because we didn't write them). However, often these code blocks expose an interrupt mechanism.
* This class provides an easy way to poll periodically to see if you should trigger this interrupt signal
*/
@Slf4j
public class ProblemIncrementor {

    public static final int POLLING_TIME_IN_MS = 1000;

    // A constantly increasing value that identifies the current problem being solved
    private final AtomicLong problemID;
    private final IPollingService pollingService;
    private final Map<Long, ScheduledFuture<?>> idToFuture;
    private final ISATFCInterruptible solver;
    private final Lock lock;

    public ProblemIncrementor(@NonNull IPollingService pollingService, @NonNull ISATFCInterruptible solver) {
        this.solver = solver;
        problemID = new AtomicLong();
        this.pollingService = pollingService;
        idToFuture = new HashMap<>();
        lock = new ReentrantLock();
    }

    /**
     * Call this method to schedule a task to periodically poll for whether or not the termination criterion says to stop. If it does, interrupt the solver.
     * Must alternate calls between this and @link{#jobDone} to cancel the polling when the problem in question is finished solving
     * @param criterion termination criterion to check
     */
    public void scheduleTermination(ITerminationCriterion criterion) {
        final long newProblemId = problemID.incrementAndGet();
        log.trace("New problem ID {}", newProblemId);
        final ScheduledFuture<?> scheduledFuture = pollingService.getService().scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    lock.lock();
                    long currentProblemId = problemID.get();
                    log.trace("Problem id is {} and we are {}", currentProblemId, newProblemId);
                    if (criterion.hasToStop() && currentProblemId == newProblemId) {
                        log.trace("Interupting problem {}", currentProblemId);
                        solver.interrupt();
                    }
                } catch (Throwable t) {
                    log.error("Caught exception in ScheduledExecutorService for interrupting problem", t);
                } finally {
                    lock.unlock();
                }
            }
        }, POLLING_TIME_IN_MS, POLLING_TIME_IN_MS, TimeUnit.MILLISECONDS);
        idToFuture.put(newProblemId, scheduledFuture);
    }

    /**
     * Cancel polling for the current job
     */
    public void jobDone() {
        try {
            lock.lock();
            final long completedJobID = problemID.getAndIncrement();
            final ScheduledFuture scheduledFuture = idToFuture.remove(completedJobID);
            if (scheduledFuture != null) {
                log.trace("Cancelling future for completed ID {}", completedJobID);
                scheduledFuture.cancel(false);
            }
        } finally {
            lock.unlock();
        }

    }

}
