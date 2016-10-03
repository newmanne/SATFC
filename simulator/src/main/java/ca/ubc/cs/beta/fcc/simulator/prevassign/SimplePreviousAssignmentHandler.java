package ca.ubc.cs.beta.fcc.simulator.prevassign;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by newmanne on 2016-09-30.
 */
public class SimplePreviousAssignmentHandler implements IPreviousAssignmentHandler {


    private Map<Integer, Integer> assignment;

    /**
     * Construct a previous assignment handler.
     */
    public SimplePreviousAssignmentHandler() {
        this.assignment = new HashMap<>();
    }

    @Override
    public Map<Integer, Integer> getPreviousAssignment() {
        return new HashMap<>(assignment);
    }

    @Override
    public Map<Integer, Integer> getPreviousAssignment(Map<Integer, Set<Integer>> domains) {
        Map<Integer, Integer> returnedAssignment = new HashMap<>();
        Set<Integer> commonDomain = Sets.intersection(assignment.keySet(), domains.keySet());
        for (Integer stationID : commonDomain) {
            Integer assignedChannel = assignment.get(stationID);
            if (domains.get(stationID).contains(assignedChannel)) {
                returnedAssignment.put(stationID, assignedChannel);
            }
        }
        return returnedAssignment;
    }

    @Override
    public void updatePreviousAssignment(Map<Integer, Integer> newAssignment) {
        this.assignment = new HashMap<>(newAssignment);
    }

}

