package ca.ubc.cs.beta.stationpacking.solvers.termination.interrupt;

import ca.ubc.cs.beta.stationpacking.solvers.decorators.ISATFCInterruptible;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import lombok.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
* Created by newmanne on 15/10/15.
*/
public class ProblemIncrementor {

    private final AtomicLong problemID;
    private final IPollingService pollingService;
    private final Map<Long, ScheduledFuture> idToFuture;
    private ISATFCInterruptible solver;

    public ProblemIncrementor(IPollingService pollingService, @NonNull ISATFCInterruptible solver) {
        this.solver = solver;
        problemID = new AtomicLong();
        this.pollingService = pollingService;
        idToFuture = new HashMap<>();
    }

    public void scheduleTermination(ITerminationCriterion criterion) {
        if (pollingService == null) {
            return;
        }
        final long newProblemId = problemID.getAndIncrement();
        final ScheduledFuture scheduledFuture = pollingService.schedule(new Runnable() {
            @Override
            public void run() {
                if (criterion.hasToStop() && problemID.get() == newProblemId) {
                    solver.interrupt();
                }
            }
        });
        idToFuture.put(newProblemId, scheduledFuture);
    }

    public void jobDone() {
        if (pollingService == null) {
            return;
        }
        final long completedJobID = problemID.getAndIncrement();
        final ScheduledFuture scheduledFuture = idToFuture.remove(completedJobID);
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
    }

}
