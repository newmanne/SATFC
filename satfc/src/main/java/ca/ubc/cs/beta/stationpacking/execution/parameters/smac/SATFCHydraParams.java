package ca.ubc.cs.beta.stationpacking.execution.parameters.smac;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import ca.ubc.cs.beta.aeatk.misc.options.OptionLevel;
import ca.ubc.cs.beta.aeatk.misc.options.UsageTextField;
import ca.ubc.cs.beta.aeatk.options.AbstractOptions;
import ca.ubc.cs.beta.stationpacking.execution.parameters.solver.sat.ClaspLibSATSolverParameters;

import com.beust.jcommander.Parameter;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

/**
 * Created by newmanne on 11/06/15.
 */
@UsageTextField(title="SMAC PARAMETERS", description = "Not intended for human use", level = OptionLevel.DEVELOPER)
public class SATFCHydraParams extends AbstractOptions {

    public enum SolverType {
        CLASP,
        UBCSAT,
        PRESOLVER,
        UNDERCONSTRAINED,
        CONNECTED_COMPONENTS,
        ARC_CONSISTENCY,
        UNSAT_PRESOLVER,
        NONE
    }

    public enum PresolverExpansion {
        NEIGHBOURHOOD, UNIFORM_RANDOM
    }

    public enum SolverChoice {
        UBCSAT, CLASP
    }
    
    @Parameter(names = "-presolver")
    public boolean presolver;
    @Parameter(names = "-presolverExpansionMethod")
    public PresolverExpansion presolverExpansionMethod;
    @Parameter(names = "-presolverNumNeighbours")
    public int presolverNumNeighbours;
    @Parameter(names = "-presolverIterativelyDeepen")
    public boolean presolverIterativelyDeepen;
    @Parameter(names = "-presolverCutoff")
    public double presolverCutoff;
    @Parameter(names = "-presolverBaseCutoff")
    public double presolverBaseCutoff;
    @Parameter(names = "-presolverScaleFactor")
    public double presolverScaleFactor;

    @Parameter(names = "-arcConsistency")
    public boolean arcConsistency;

    @Parameter(names = "-underconstrained")
    public boolean underconstrained;

    @Parameter(names = "-connectedComponents")
    public boolean connectedComponents;

    @Parameter(names = "-solverChoice")
    public SolverChoice solverChoice;

    @Parameter(names = "-presolverType")
    public SolverType presolverType;

    public String claspConfig;
    public String ubcsatConfig;

    public List<SolverType> getSolverOrder() {
        final List<SolverType> list = new ArrayList<>();
        if (arcConsistency) {
            list.add(SolverType.ARC_CONSISTENCY);
        }
        if (underconstrained) {
            list.add(SolverType.UNDERCONSTRAINED);
        }
        if (connectedComponents) {
            list.add(SolverType.CONNECTED_COMPONENTS);
        }
        if (presolver) {
            list.add(SolverType.PRESOLVER);
        } else {
            list.add(solverChoice.equals(SolverChoice.CLASP) ? SolverType.CLASP : SolverType.UBCSAT);
        }
        return list;
    }

    public boolean validate() {
        Preconditions.checkNotNull(claspConfig);
        Preconditions.checkState(presolverType == null || presolverType.equals(SolverType.PRESOLVER) || presolverType.equals(SolverType.UNSAT_PRESOLVER));
        return true;
    }

}
