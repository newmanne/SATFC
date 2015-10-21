package ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories;

import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.jnalibraries.UBCSATLibrary;

/**
* Created by newmanne on 03/09/15.
*/
public class UBCSATLibraryGenerator extends NativeLibraryGenerator<UBCSATLibrary> {

    public UBCSATLibraryGenerator(String libraryPath) {
        super(libraryPath, UBCSATLibrary.class);
    }
}
