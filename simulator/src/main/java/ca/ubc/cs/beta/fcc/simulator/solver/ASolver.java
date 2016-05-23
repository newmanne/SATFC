package ca.ubc.cs.beta.fcc.simulator.solver;

import ca.ubc.cs.beta.fcc.simulator.Simulator;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SATFCCallback;
import ca.ubc.cs.beta.fcc.simulator.station.StationInfo;

import java.util.Map;
import java.util.Set;

/**
 * Created by newmanne on 2016-05-20.
 */
public abstract class ASolver implements ISolver {

    private final Simulator.ISATFCProblemSpecGenerator problemSpecGenerator;

    public ASolver(Simulator.ISATFCProblemSpecGenerator problemSpecGenerator) {
        this.problemSpecGenerator = problemSpecGenerator;
    }

    @Override
    public void getFeasibility(Set<StationInfo> stations, Map<Integer, Integer> previousAssignment, SATFCCallback callback) {
        final Simulator.SATFCProblemSpecification problem = problemSpecGenerator.createProblem(stations, previousAssignment);
        solve(problem, callback);
    }

    protected abstract void solve(Simulator.SATFCProblemSpecification problemSpecification, SATFCCallback callback);

}
