package ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories;

import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.jnalibraries.Clasp3Library;

/**
* Created by newmanne on 03/09/15.
*/
public class Clasp3LibraryGenerator extends NativeLibraryGenerator<Clasp3Library> {

    public Clasp3LibraryGenerator(String libraryPath) {
        super(libraryPath, Clasp3Library.class);
    }

}
