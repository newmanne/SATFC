package ca.ubc.cs.beta.stationpacking.solvers.composites;

import ca.ubc.cs.beta.stationpacking.solvers.ISolver;

/**
* Created by newmanne on 26/05/15.
*/
public interface ISolverFactory {
    ISolver create();
}
