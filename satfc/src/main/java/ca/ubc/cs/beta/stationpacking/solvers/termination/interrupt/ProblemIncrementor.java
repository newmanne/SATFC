package ca.ubc.cs.beta.stationpacking.solvers.termination.interrupt;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.ISATFCInterruptible;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;

/**
* Created by newmanne on 15/10/15.
* Some SATFC methods delve into long blocks of code that will not periodically check for interrupt signals (because we didn't write them). However, often these code blocks expose an interrupt mechanism.
* This class provides an easy way to poll periodically to see if you should trigger this interrupt signal
*/
@Slf4j
public class ProblemIncrementor {

    private final AtomicLong problemID;
    private final IPollingService pollingService;
    private final Map<Long, ScheduledFuture<?>> idToFuture;
    private ISATFCInterruptible solver;
    private final Lock lock;

    public ProblemIncrementor(IPollingService pollingService, @NonNull ISATFCInterruptible solver) {
        this.solver = solver;
        problemID = new AtomicLong();
        this.pollingService = pollingService;
        idToFuture = new HashMap<>();
        lock = new ReentrantLock();
    }

    public void scheduleTermination(ITerminationCriterion criterion) {
        if (pollingService == null) {
            return;
        }
        final long newProblemId = problemID.incrementAndGet();
        log.trace("New problem ID {}", newProblemId);
        final ScheduledFuture<?> scheduledFuture = pollingService.schedule(new Runnable() {
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
        });
        idToFuture.put(newProblemId, scheduledFuture);
    }

    public void jobDone() {
        if (pollingService == null) {
            return;
        }
        try {
            lock.lock();
            final long completedJobID = problemID.getAndIncrement();
            final ScheduledFuture scheduledFuture = idToFuture.remove(completedJobID);
            log.trace("Cancelling future for completed ID {}", completedJobID);
            scheduledFuture.cancel(false);
        } finally {
            lock.unlock();
        }

    }

}
