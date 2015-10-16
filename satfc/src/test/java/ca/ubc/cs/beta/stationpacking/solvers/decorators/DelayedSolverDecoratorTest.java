package ca.ubc.cs.beta.stationpacking.solvers.decorators;

import ca.ubc.cs.beta.stationpacking.StationPackingTestUtils;
import ca.ubc.cs.beta.stationpacking.solvers.VoidSolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.termination.walltime.WalltimeTerminationCriterion;
import ca.ubc.cs.beta.stationpacking.utils.Watch;
import org.junit.Test;

import static org.junit.Assert.*;

public class DelayedSolverDecoratorTest {

    @Test
    public void testDelay() throws Exception {
        final DelayedSolverDecorator delayedSolverDecorator = new DelayedSolverDecorator(new VoidSolver(), 0.1);
        final Watch watch = Watch.constructAutoStartWatch();
        delayedSolverDecorator.solve(StationPackingTestUtils.getSimpleInstance(), new WalltimeTerminationCriterion(10), 1);
        assertTrue("Watch time was " + watch.getElapsedTime(), watch.getElapsedTime() >= 0.1);
    }

    @Test
    public void testInterrupt() throws Exception {
        final Watch watch = Watch.constructAutoStartWatch();
        final DelayedSolverDecorator delayedSolverDecorator = new DelayedSolverDecorator(new VoidSolver(), 90);
        new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                delayedSolverDecorator.interrupt();
            }
        }.start();
        final SolverResult solve = delayedSolverDecorator.solve(StationPackingTestUtils.getSimpleInstance(), new WalltimeTerminationCriterion(90), 1);
        final double time = watch.getElapsedTime();
        assertTrue("Watch time was " + time + " expected less since interruption", time <= 1);
    }

}