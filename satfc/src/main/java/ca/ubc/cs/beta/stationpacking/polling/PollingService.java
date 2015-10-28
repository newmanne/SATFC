package ca.ubc.cs.beta.stationpacking.polling;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

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
    public ScheduledExecutorService getService() {
        return scheduledExecutorService;
    }

    @Override
    public void notifyShutdown() {
        scheduledExecutorService.shutdown();
    }


}
