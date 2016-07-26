package ca.ubc.cs.beta.fcc.simulator.solver;

import ca.ubc.cs.beta.fcc.simulator.Simulator;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SATFCCallback;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.station.StationInfo;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader;

import java.util.Map;
import java.util.Set;

/**
 * Created by newmanne on 2016-05-20.
 */
public abstract class AFeasibilitySolver implements IFeasibilitySolver {

    private final Simulator.ISATFCProblemSpecGenerator problemSpecGenerator;

    public AFeasibilitySolver(Simulator.ISATFCProblemSpecGenerator problemSpecGenerator) {
        this.problemSpecGenerator = problemSpecGenerator;
    }

    @Override
    public void getFeasibility(Set<IStationInfo> stations, Map<Integer, Integer> previousAssignment, SATFCCallback callback) {
        final SimulatorProblemReader.SATFCProblemSpecification problem = problemSpecGenerator.createProblem(stations, previousAssignment);
        solve(problem, callback);
    }

    protected abstract void solve(SimulatorProblemReader.SATFCProblemSpecification problemSpecification, SATFCCallback callback);

}
