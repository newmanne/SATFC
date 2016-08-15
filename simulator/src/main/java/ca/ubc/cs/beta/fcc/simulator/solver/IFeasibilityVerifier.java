package ca.ubc.cs.beta.fcc.simulator.solver;

import java.util.Map;

/**
 * Created by newmanne on 2016-07-29.
 */
public interface IFeasibilityVerifier {

    boolean isFeasibleAssignment(Map<Integer, Integer> stationToChannel);

}
