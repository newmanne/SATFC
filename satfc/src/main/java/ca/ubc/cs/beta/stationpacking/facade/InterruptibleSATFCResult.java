package ca.ubc.cs.beta.stationpacking.facade;

import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import com.google.common.base.Preconditions;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
* Created by newmanne on 13/10/15.
*/
public class InterruptibleSATFCResult {

    private final ITerminationCriterion.IInterruptibleTerminationCriterion criterion;
    private final Callable<SATFCResult> solveTask;

    public InterruptibleSATFCResult(ITerminationCriterion.IInterruptibleTerminationCriterion criterion, Callable<SATFCResult> solveTask) {
        this.criterion = criterion;
        this.solveTask = solveTask;
    }

    /**
     * Interrupt the solve operation associated with this event.
     * This method returns immediately, even though the interruption signal might not have been read by SATFC yet
     * It is safe to call this method multiple times (but it will have no effect past the first time).
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
            return solveTask.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}