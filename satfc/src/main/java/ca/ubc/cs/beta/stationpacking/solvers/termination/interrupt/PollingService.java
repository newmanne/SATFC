package ca.ubc.cs.beta.stationpacking.solvers.termination.interrupt;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import ca.ubc.cs.beta.aeatk.concurrent.threadfactory.SequentiallyNamedThreadFactory;

/**
* Created by newmanne on 15/10/15.
*/
public class PollingService implements IPollingService {

    private final ScheduledExecutorService scheduledExecutorService;

    public PollingService() {
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(new SequentiallyNamedThreadFactory("SATFC Interrupt Polling"));
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable runnable) {
        return scheduledExecutorService.scheduleWithFixedDelay(runnable, 1000, 1000, TimeUnit.MILLISECONDS);
    }

    @Override
    public ScheduledExecutorService getService() {
        return scheduledExecutorService;
    }

    @Override
    public void notifyShutdown() {
        scheduledExecutorService.shutdown();
    }


}
