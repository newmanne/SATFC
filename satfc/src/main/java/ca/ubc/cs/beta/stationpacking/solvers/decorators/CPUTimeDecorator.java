package ca.ubc.cs.beta.stationpacking.solvers.decorators;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.jnalibraries.Clasp3Library;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;

/**
 * Created by newmanne on 05/10/15.
 */
public class CPUTimeDecorator extends ASolverDecorator {

    private final Clasp3Library library;

    public CPUTimeDecorator(ISolver aSolver, Clasp3Library library) {
        super(aSolver);
        this.library = library;
    }

    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
        double startCpuTime = library.getCpuTime();
        final SolverResult solve = super.solve(aInstance, aTerminationCriterion, aSeed);
        double cpuTime = library.getCpuTime() - startCpuTime;
        System.out.println("CPU TIME FOR HYDRA: " + cpuTime);
        return solve;
    }
}
