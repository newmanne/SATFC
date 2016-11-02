package ca.ubc.cs.beta.fcc.simulator.prevassign;

import java.util.Map;
import java.util.Set;

/**
 * Created by newmanne on 2016-08-04.
 */
public interface IPreviousAssignmentHandler {

    Map<Integer, Integer> getPreviousAssignment();
    /*
     * Return previous assignment restricted to stations which appear in the domains
     */
    Map<Integer, Integer> getPreviousAssignment(Map<Integer, Set<Integer>> domains);
    void updatePreviousAssignment(Map<Integer, Integer> previousAssignment);

}
