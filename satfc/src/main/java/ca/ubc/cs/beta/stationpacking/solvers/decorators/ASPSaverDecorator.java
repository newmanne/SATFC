package ca.ubc.cs.beta.stationpacking.solvers.decorators;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.Constraint;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.yaml.EncodingType;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import com.google.common.collect.Lists;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.*;

/**
 * Created by newmanne on 2016-09-20.
 */
public class ASPSaverDecorator extends ASolverDecorator {

    private final IConstraintManager fConstraintManager;
    private final String aspDir;

    /**
     * @param aSolver - decorated ISolver.
     * @param fConstraintManager
     */
    public ASPSaverDecorator(ISolver aSolver, IConstraintManager fConstraintManager, String aspDir) {
        super(aSolver);
        this.fConstraintManager = fConstraintManager;
        this.aspDir = aspDir;
    }

    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
        final Map<Station, Set<Integer>> domains = aInstance.getDomains();

        final StringBuilder sb = new StringBuilder();

        // Domains
        sb.append("% Domains").append(System.lineSeparator());
        for (Map.Entry<Station, Set<Integer>> entry : aInstance.getDomains().entrySet()) {
            for (int chan : entry.getValue()) {
                sb.append("domain(").append("s").append(entry.getKey().getID()).append(",").append(chan).append(").").append(System.lineSeparator());
            }
        }

        // Constraints -
        sb.append("% Constraints").append(System.lineSeparator());
        for (Constraint constraint : fConstraintManager.getAllRelevantConstraints(domains)) {
            addConstraint(sb, constraint.getSource().getID(), constraint.getTarget().getID(), constraint.getSourceChannel(), constraint.getTargetChannel() - constraint.getSourceChannel());
        }

        try {
            FileUtils.writeStringToFile(new File(aspDir + File.separator + aInstance.getName() + ".lp"), sb.toString());
        } catch (IOException e) {
            throw new IllegalStateException("Could not write ASP to file", e);
        }

        return super.solve(aInstance, aTerminationCriterion, aSeed);
    }

    private void addConstraint(StringBuilder sb, int source, int target, int channel, int adj) {
        if (adj == 0) {
            sb.append("co(").append("s").append(source).append(",").append("s").append(target).append(",").append(channel).append(").").append(System.lineSeparator());
        } else {
            sb.append("adj(").append("s").append(source).append(",").append("s").append(target).append(",").append(channel).append(",").append(adj).append(").").append(System.lineSeparator());
        }
    }

}
