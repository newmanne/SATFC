package ca.ubc.cs.beta.fcc.simulator.prevassign;

import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.fcc.simulator.utils.BandHelper;
import com.google.common.base.Preconditions;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by newmanne on 2016-08-04.
 */
public interface IPreviousAssignmentHandler {

    Map<Integer, Integer> getPreviousAssignment();
    default Map<Integer, Integer> getPreviousAssignment(Band band) {
        Preconditions.checkState(band.isAirBand());
        final Map<Integer, Integer> previousAssignment = getPreviousAssignment();
        return previousAssignment.entrySet().stream()
                .filter(e -> BandHelper.toBand(e.getValue()).equals(band))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
    /*
     * Return previous assignment restricted to stations which appear in the domains
     */
    Map<Integer, Integer> getPreviousAssignment(Map<Integer, Set<Integer>> domains);
    void updatePreviousAssignment(Map<Integer, Integer> previousAssignment);

}
