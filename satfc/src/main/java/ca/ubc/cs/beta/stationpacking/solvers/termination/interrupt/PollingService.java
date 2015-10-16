package ca.ubc.cs.beta.stationpacking.solvers.termination.interrupt;

import ca.ubc.cs.beta.aeatk.concurrent.threadfactory.SequentiallyNamedThreadFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
* Created by newmanne on 15/10/15.
*/
public class PollingService implements IPollingService {

    private final ScheduledExecutorService scheduledExecutorService;

    public PollingService() {
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(new SequentiallyNamedThreadFactory("SATFC Interrupt Polling"));
    }

    public ScheduledFuture schedule(Runnable runnable) {
        return scheduledExecutorService.scheduleAtFixedRate(runnable, 1000, 1000, TimeUnit.MILLISECONDS);
    }

}
