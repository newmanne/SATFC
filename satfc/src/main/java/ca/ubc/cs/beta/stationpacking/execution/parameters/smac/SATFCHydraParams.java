package ca.ubc.cs.beta.stationpacking.execution.parameters.smac;

import ca.ubc.cs.beta.aeatk.misc.options.UsageTextField;
import ca.ubc.cs.beta.aeatk.options.AbstractOptions;
import ca.ubc.cs.beta.stationpacking.execution.parameters.solver.sat.ClaspLibSATSolverParameters;
import com.beust.jcommander.Parameter;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by newmanne on 11/06/15.
 */
@UsageTextField(title="SMAC PARAMETERS", description = "Not intended for human use")
public class SATFCHydraParams extends AbstractOptions {

    public enum SolverType {
        CLASP,
        PRESOLVER,
        UNDERCONSTRAINED,
        CONNECTED_COMPONENTS,
        ARC_CONSISTENCY,
        NONE
    }

    public enum ClaspConfig {

        H1 (ClaspLibSATSolverParameters.UHF_CONFIG_04_15_h1),
        H2 (ClaspLibSATSolverParameters.UHF_CONFIG_04_15_h2);

        private final String config;

        ClaspConfig(String config) {
            this.config = config;
        }

        public String getConfig() {
            return config;
        }
    }

    @Parameter(names = "-presolver")
    public boolean presolver;
    @Parameter(names = "-presolverCutoff")
    public double presolverCutoff;
    @Parameter(names = "-presolverClaspConfig")
    public ClaspConfig presolverClaspConfig;

    @Parameter(names = "-claspClaspConfig")
    public ClaspConfig claspClaspConfig;

    @Parameter(names = "-opt1")
    public SolverType opt1;
    @Parameter(names = "-opt2")
    public SolverType opt2;
    @Parameter(names = "-opt3")
    public SolverType opt3;

    public List<SolverType> getSolverOrder() {
        final List<SolverType> list = new ArrayList<>();
        if (opt1 != null) {
            list.add(opt1);
        }
        if (opt2 != null) {
            list.add(opt2);
        }
        if (opt3 != null) {
            list.add(opt3);
        }
        if (presolver) {
            list.add(SolverType.PRESOLVER);
        }
        list.add(SolverType.CLASP);
        return list;
    }

    public boolean validate() {
        Preconditions.checkNotNull(claspClaspConfig);
        return true;
    }

}
