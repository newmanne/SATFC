package ca.ubc.cs.beta.fcc.simulator.solver.decorator;

import ca.ubc.cs.beta.aeatk.misc.cputime.CPUTime;
import ca.ubc.cs.beta.fcc.simulator.solver.IFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SATFCCallback;
import ca.ubc.cs.beta.fcc.simulator.solver.problem.SimulatorProblem;
import ca.ubc.cs.beta.fcc.simulator.state.SaveStateToFile;
import ca.ubc.cs.beta.fcc.simulator.time.TimeTracker;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.stationpacking.utils.Watch;
import com.google.common.eventbus.Subscribe;
import humanize.Humanize;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by newmanne on 2016-06-15.
 */
@Slf4j
public class TimeTrackerFeasibilitySolverDecorator extends AFeasibilitySolverDecorator {

    private final Watch simulatorTimeOnlyWatch;
    private final Watch overallTimeWatch;
    private CPUTime cpuTime;
    private double cpuTimeCount;
    private final double initTime;
    private TimeTracker UHFTimeTracker;
    private int greedySolvedUHF;
    private TimeTracker VHFTimeTracker;

    public TimeTrackerFeasibilitySolverDecorator(IFeasibilitySolver decorated, Watch simulatorTimeOnlyWatch, CPUTime cputime) {
        super(decorated);
        this.simulatorTimeOnlyWatch = simulatorTimeOnlyWatch;
        initTime = simulatorTimeOnlyWatch.getElapsedTime();
        overallTimeWatch = Watch.constructAutoStartWatch();
        this.cpuTime = cputime;
        cpuTimeCount = 0.;
        this.UHFTimeTracker = new TimeTracker();
        greedySolvedUHF = 0;
        this.VHFTimeTracker = new TimeTracker();
    }

    @Override
    public void getFeasibility(SimulatorProblem problem, SATFCCallback callback) {
        simulatorTimeOnlyWatch.stop();
        cpuTimeCount += cpuTime.getCPUTime();
        super.getFeasibility(problem, (p, result) -> {
            simulatorTimeOnlyWatch.start();
            if (p.getBand().equals(Band.UHF) && !result.isCached()) {
                UHFTimeTracker.update(result.getSATFCResult());
                if (result.isGreedySolved()) {
                    greedySolvedUHF += 1;
                }
            } else if (p.getBand().isVHF()) {
                VHFTimeTracker.update(result.getSATFCResult());
            }
            cpuTime = new CPUTime();
            callback.onSuccess(p, result);
        });
    }

    @Subscribe
    public void onReportState(SaveStateToFile.ReportStateEvent event) {
        event.getBuilder()
                .simulatorTime(simulatorTimeOnlyWatch.getElapsedTime())
                .simulatorCPUTime(getCpuTime())
                .overallWalltime(getOverallTime())
                .problemCPUTime(UHFTimeTracker.getCputime().get())
                .nProblems(UHFTimeTracker.getNProblems().get())
                .greedySolved(greedySolvedUHF)
                .problemWallTime(UHFTimeTracker.getWalltime().get());
    }

    private double getOverallTime() {
        return initTime + overallTimeWatch.getElapsedTime();
    }

    private double getCpuTime() {
        return cpuTimeCount + cpuTime.getCPUTime();
    }

    public void report() {
        log.info("-----------------------------------------------");
        log.info("Simulator has been running for a wall time of {}", Humanize.duration(getOverallTime()));
        log.info("JVM has been running for {} CPU time", Humanize.duration(CPUTime.getCPUTimeSinceJVMStart()));
        log.info("Time spent on simulator only: Wall: {}, CPU: {}", Humanize.duration(simulatorTimeOnlyWatch.getElapsedTime()), Humanize.duration(getCpuTime()));
        log.info("(Sequential Time) Time spent on UHF problems only: Wall: {}, CPU: {}", Humanize.duration(UHFTimeTracker.getWalltime()), Humanize.duration(UHFTimeTracker.getCputime()));
        log.info("# (non-repeat) UHF Problems: {}", Humanize.spellBigNumber(UHFTimeTracker.getNProblems()));
        log.info("# Greedy Solved UHF Problems {}, {}", Humanize.spellBigNumber(greedySolvedUHF), Humanize.formatPercent((double) greedySolvedUHF / UHFTimeTracker.getNProblems().get()));
        int nonGreedy = UHFTimeTracker.getNProblems().get() - greedySolvedUHF;
        log.info("# Non-Greedy UHF Problems {}, {}", Humanize.spellBigNumber(nonGreedy), Humanize.formatPercent((double) nonGreedy / UHFTimeTracker.getNProblems().get()));
        log.info("Time spent on {} VHF problems: Wall: {}, CPU: {}", Humanize.spellBigNumber(VHFTimeTracker.getNProblems()), Humanize.duration(VHFTimeTracker.getWalltime()), Humanize.duration(VHFTimeTracker.getCputime()));
        log.info("-----------------------------------------------");
    }

}
