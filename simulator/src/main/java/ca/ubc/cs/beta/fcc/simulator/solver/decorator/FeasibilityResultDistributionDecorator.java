package ca.ubc.cs.beta.fcc.simulator.solver.decorator;

import ca.ubc.cs.beta.fcc.simulator.solver.IFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SATFCCallback;
import ca.ubc.cs.beta.fcc.simulator.solver.problem.SimulatorProblem;
import ca.ubc.cs.beta.fcc.simulator.state.SaveStateToFile;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import com.google.common.collect.HashMultiset;
import com.google.common.eventbus.Subscribe;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by newmanne on 2016-06-23.
 */
@Slf4j
public class FeasibilityResultDistributionDecorator extends AFeasibilitySolverDecorator {

    public static class FeasibilityResultDistribution {

        private final HashMultiset<SATResult> distribution;

        public FeasibilityResultDistribution() {
            distribution = HashMultiset.create();
        }

        public void update(SATResult satResult) {
            distribution.add(satResult);
        }

        public void report() {
            log.info("Feasibility counts: {}", histogram());
        }

        public Map<SATResult, Integer> histogram() {
            return distribution.elementSet().stream().collect(Collectors.toMap(e -> e, distribution::count));
        }

    }

    private final FeasibilityResultDistribution distribution;

    public FeasibilityResultDistributionDecorator(IFeasibilitySolver decorated, FeasibilityResultDistribution distribution) {
        super(decorated);
        this.distribution = distribution;
    }

    @Override
    public void getFeasibility(SimulatorProblem problem, SATFCCallback callback) {
        super.getFeasibility(problem, (p, result) -> {
            distribution.update(result.getSATFCResult().getResult());
            callback.onSuccess(p, result);
        });
    }

    @Subscribe
    public void onReportState(SaveStateToFile.ReportStateEvent event) {
        event.getBuilder()
                .feasibilityDistribution(distribution.histogram());
    }


}
