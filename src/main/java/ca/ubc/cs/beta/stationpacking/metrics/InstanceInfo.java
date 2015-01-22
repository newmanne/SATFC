package ca.ubc.cs.beta.stationpacking.metrics;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Set;

/**
 * Created by newmanne on 21/01/15.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class InstanceInfo {

    private int numStations;
    private Set<Integer> stations;
    private String name;
    private Double runtime;
    private SATResult result;
    private Set<Integer> underconstrainedStations;
    private List<InstanceInfo> components = Lists.newArrayList();
    private Boolean solvedByPresolver;

}
