package ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.jnalibraries;

import com.sun.jna.Pointer;

/**
 * Created by pcernek on 7/29/15.
 */
public interface UBCSATTestHelperLibrary extends UBCSATLibrary {

    /**
     * Gets the number of variables in the current SAT problem according to UBCSAT.
     *  Must be called after {@link #initProblem(Pointer, String)}.
     * @return the number of variables in the current SAT problem
     */
    int getNumVars();

    /**
     * Gets the number of clauses in the current SAT problem according to UBCSAT.
     *  Must be called after {@link #initProblem(Pointer, String)}.
     * @return the number of clauses in the current SAT problem
     */
    int getNumClauses();

    /**
     * Get the current assignment (True or False) of the given variable.
     * @param varNumber the number of the variable for which to get the assignment.
     * @return the given variable's assignment.
     */
    boolean getVarAssignment(int varNumber);

    /**
     * Runs the triggers associated with the InitData event point in UBCSAT.
     * Must be run after initProblem.
     */
    void runInitData();
}
