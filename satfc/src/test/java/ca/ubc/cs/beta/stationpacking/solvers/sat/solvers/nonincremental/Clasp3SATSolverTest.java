package ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.nonincremental;

import lombok.extern.slf4j.Slf4j;

import org.junit.Test;

import ca.ubc.cs.beta.stationpacking.facade.SATFCFacadeBuilder;

@Slf4j
public class Clasp3SATSolverTest {

    @Test(expected = IllegalArgumentException.class)
    public void testFailOnInvalidParameters() {
        final String libraryPath = SATFCFacadeBuilder.findSATFCLibrary();
        final String parameters = "these are not valid parameters";
        log.info(libraryPath);
        new Clasp3SATSolver(libraryPath, parameters);
    }

}