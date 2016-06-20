package ca.ubc.cs.beta.fcc.simulator.time;

import ca.ubc.cs.beta.aeatk.misc.cputime.CPUTime;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
import ca.ubc.cs.beta.stationpacking.utils.Watch;
import com.google.common.util.concurrent.AtomicDouble;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by newmanne on 2016-06-15.
 */
@Slf4j
public class TimeTracker {

    @Getter
    private AtomicDouble cputime;
    @Getter
    private AtomicDouble walltime;
    @Getter
    private AtomicInteger nProblems;

    private final Watch simulatorWatch;

    public TimeTracker() {
        cputime = new AtomicDouble();
        walltime = new AtomicDouble();
        nProblems = new AtomicInteger();
        simulatorWatch = Watch.constructAutoStartWatch();
    }

    public void update(SATFCResult result) {
        cputime.addAndGet(result.getCputime());
        walltime.addAndGet(result.getRuntime());
        nProblems.incrementAndGet();
    }

    public void report() {
        // double counts CPU time if running solvers locally
        log.info(String.format("CPU: Simulator %.2f s, Problems %.2f s", CPUTime.getCPUTimeSinceJVMStart(), getCputime().get()));
        log.info(String.format("Wall: Simulator + Problems %.2f s, Problems %.2f s", simulatorWatch.getElapsedTime(), getWalltime().get()));
        log.info("# Problems: {}", getNProblems());
    }

}
