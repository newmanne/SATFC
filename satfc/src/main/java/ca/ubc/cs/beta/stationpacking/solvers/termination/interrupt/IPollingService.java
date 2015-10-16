package ca.ubc.cs.beta.stationpacking.solvers.termination.interrupt;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

/**
* Created by newmanne on 15/10/15.
*/
public interface IPollingService {

    ScheduledFuture<?> schedule(Runnable runnable);

    ScheduledExecutorService getService();

}
