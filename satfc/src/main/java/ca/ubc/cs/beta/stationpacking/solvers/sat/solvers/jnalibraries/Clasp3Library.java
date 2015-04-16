package ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.jnalibraries;

import com.sun.jna.Library;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

/**
* Created by newmanne on 15/04/15.
*/
public interface Clasp3Library extends Library {

    Pointer initConfig(final String params);

    void initProblem(Pointer jnaProblemPointer, final String problemString);

    void solveProblem(Pointer jnaProblemPointer, double timeoutTime);

    void destroyProblem(Pointer jnaProblemPointer);

    boolean interrupt(Pointer jnaProblemPointer);

    int getResultState(Pointer jnaProblemPointer);

    int getConfigState(Pointer jnaProblemPointer);

    IntByReference getResultAssignment(Pointer jnaProblemPointer);

    String getConfigErrorMessage(Pointer jnaProblemPointer);
}