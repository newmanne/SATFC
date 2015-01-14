package ca.ubc.cs.beta.stationpacking.execution.parameters.solver;

import ca.ubc.cs.beta.aeatk.misc.options.UsageTextField;
import ca.ubc.cs.beta.aeatk.options.AbstractOptions;
import ca.ubc.cs.beta.stationpacking.facade.SATFCFacadeParameter;
import com.beust.jcommander.Parameter;

/**
 * Created by newmanne on 13/01/15.
 */
@UsageTextField(title="Solver customization options",description="Parameters describing which optimizations to apply")
public class SolverCustomizationOptionsParameters {


        @Parameter(names = "--no-presolve", description = "disable pre-solving")
        private boolean noPresolve;
        @Parameter(names = "--no-underconstrained", description = "disable underconstrained station optimizations")
        private boolean noUnderconstrained;
        @Parameter(names = "--no-decomposition", description = "disable connected component decomposition")
        private boolean noDecompose;

        public SATFCFacadeParameter.SolverCustomizationOptions getOptions() {
            SATFCFacadeParameter.SolverCustomizationOptions options = new SATFCFacadeParameter.SolverCustomizationOptions();
            options.setPresolve(!noPresolve);
            options.setUnderconstrained(!noUnderconstrained);
            options.setDecompose(!noDecompose);
            return options;
        }

}
