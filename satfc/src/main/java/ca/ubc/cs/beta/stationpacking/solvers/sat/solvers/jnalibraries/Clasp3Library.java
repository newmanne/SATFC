package ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.jnalibraries;

import com.sun.jna.Library;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

/**
* Created by newmanne on 15/04/15.
*/
public interface Clasp3Library extends Library {

    Pointer initProblem(final String params, final String problem);

    void solveProblem(Pointer problem, double timeoutTime);

    void destroyProblem(Pointer problem);

    boolean interrupt(Pointer problem);

    int getResultState(Pointer problem);

    IntByReference getResultAssignment(Pointer problem);
}
