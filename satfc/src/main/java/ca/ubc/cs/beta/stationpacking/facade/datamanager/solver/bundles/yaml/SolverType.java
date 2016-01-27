package ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.yaml;

/**
* Created by newmanne on 27/10/15.
*/
public enum SolverType {
    CLASP,
    SATENSTEIN,
    SAT_PRESOLVER,
    UNSAT_PRESOLVER,
    UNDERCONSTRAINED,
    CONNECTED_COMPONENTS,
    ARC_CONSISTENCY,
    PYTHON_VERIFIER,
    VERIFIER,
    CACHE,
    SAT_CACHE,
    UNSAT_CACHE,
    PARALLEL,
    RESULT_SAVER,
    CNF,
    CHANNEL_KILLER,
    DELAY,
    TIME_BOUNDED,
    HASH_INDEX,
    PREVIOUS_ASSIGNMENT,
    NONE
}
