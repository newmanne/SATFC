package ca.ubc.cs.beta.stationpacking.facade;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;

/**
* Created by newmanne on 13/10/15.
*/
public class InterruptibleSATFCResult {

    private final ITerminationCriterion.IInterruptibleTerminationCriterion criterion;
    private final Callable<SATFCResult> solveTask;
    private final CountDownLatch latch;

    public InterruptibleSATFCResult(ITerminationCriterion.IInterruptibleTerminationCriterion criterion, Callable<SATFCResult> solveTask) {
        this.criterion = criterion;
        this.solveTask = solveTask;
        latch = new CountDownLatch(1);
    }

    /**
     * Interrupt the solve operation associated with this event.
     * This method returns immediately, even though the interruption signal might not have been read by SATFC yet
     * It is safe to call this method multiple times (but it will have no effect past the first time).
     * It is safe to call this method if the problem has not started, or if the problem has finished
     * Note that an interrupted result will look identical to a TIMEOUT (except that it may not actually match the cutoff)
     */
    public void interrupt() {
        criterion.interrupt();
    }

    /**
     * Call this method to begin computing the problem. The problem will not start computation until this is called.
     * Blocks until the problem is completed
     * @return The SATFCResult
     */
    public SATFCResult computeResult() {
        try {
            final SATFCResult call = solveTask.call();
            latch.countDown();
            return call;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Blocks until the problem is terminated (and the SATFCFacade is safe to use to start another problem.
     * Note that {@link #computeResult()} should be used to solve the problem, this is for another thread that might want to wait
     */
    public void blockUntilTerminated() throws InterruptedException {
        latch.await();
    }

}