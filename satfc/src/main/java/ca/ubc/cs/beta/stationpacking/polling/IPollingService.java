package ca.ubc.cs.beta.stationpacking.polling;

import java.util.concurrent.ScheduledExecutorService;

/**
* Created by newmanne on 15/10/15.
* Wrapper to hold a ScheduledExecutor for polling jobs
*/
public interface IPollingService {

    ScheduledExecutorService getService();

    void notifyShutdown();

}
