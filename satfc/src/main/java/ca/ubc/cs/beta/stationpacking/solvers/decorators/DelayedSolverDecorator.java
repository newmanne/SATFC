package ca.ubc.cs.beta.stationpacking.solvers.decorators;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.utils.Watch;
import lombok.extern.slf4j.Slf4j;
import sun.util.resources.cldr.aa.CalendarData_aa_ER;

import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by newmanne on 25/05/15.
 * Delays the solve operation by a specified amount
 * Used in parallel solvers, where one solver isn't interruptable (because it has blocking calls into a library we have no control of, for example), where you would rather only run this
 * branch if it looks like it's going to be a particularly long run
 */
@Slf4j
public class DelayedSolverDecorator extends ASolverDecorator {
    private final long delay;

    private final Condition interrupted;
    private final Lock lock;

    /**
     * @param aSolver - decorated ISolver.
     */
    public DelayedSolverDecorator(ISolver aSolver, final double delay) {
        super(aSolver);
        this.delay = (long) (1000 * delay);
        this.lock = new ReentrantLock();
        this.interrupted = lock.newCondition();
    }

    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
        final Watch watch = Watch.constructAutoStartWatch();
        lock.lock();
        try {
            log.trace("Going to sleep for {} millis, or until someone signals me ", delay);
            if (!aTerminationCriterion.hasToStop()) {
                interrupted.await(delay, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException ignored) {
        } finally {
            lock.unlock();
        }
        log.trace("I'm awake! Passing on to the next solver.");
        final double addTime = watch.getElapsedTime();
        final SolverResult result = super.solve(aInstance, aTerminationCriterion, aSeed);
        return SolverResult.addTime(result, addTime);
    }

    @Override
    public void interrupt() {
        log.trace("Awakening the sleeping thread");
        lock.lock();
        interrupted.signalAll();
        lock.unlock();
        log.trace("Sleeping thread has been signalled");
        super.interrupt();
    }
}
