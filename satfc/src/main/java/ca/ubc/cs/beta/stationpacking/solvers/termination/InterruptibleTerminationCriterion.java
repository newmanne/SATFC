package ca.ubc.cs.beta.stationpacking.solvers.termination;

import java.util.concurrent.atomic.AtomicBoolean;

/**`
 * Created by newmanne on 13/05/15.
 */
public class InterruptibleTerminationCriterion implements ITerminationCriterion.IInterruptibleTerminationCriterion {

    private final ITerminationCriterion decoratedCriterion;
    private final AtomicBoolean interrupt;

    public InterruptibleTerminationCriterion(ITerminationCriterion decoratedCriterion) {
        this.decoratedCriterion = decoratedCriterion;
        this.interrupt = new AtomicBoolean(false);
    }

    @Override
    public double getRemainingTime() {
        return decoratedCriterion.getRemainingTime();
    }

    @Override
    public boolean hasToStop() {
        return interrupt.get() || decoratedCriterion.hasToStop();
    }

    @Override
    public void notifyEvent(double aTime) {
        decoratedCriterion.notifyEvent(aTime);
    }

    public void interrupt() {
        interrupt.set(true);
    }

}
