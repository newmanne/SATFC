package ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles;

import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.jnalibraries.Clasp3Library;
import ca.ubc.cs.beta.stationpacking.utils.NativeUtils;
import com.google.common.io.Files;
import com.sun.jna.Native;

import java.io.File;
import java.io.IOException;

/**
 * This class returns a fresh, independent clasp library to each thread
 * We need to use this class because clasp is (probably?) not re-entrant
 * Because of the way JNA works, we need a new physical copy of the library each time we launch
 */
public class ClaspLibraryGenerator {
    private final String libraryPath;
    private int numClasps;

    public ClaspLibraryGenerator(String libraryPath) {
        this.libraryPath = libraryPath;
        numClasps = 0;
    }

    public Clasp3Library createClaspLibrary() {
        File origFile = new File(libraryPath);
        try {
            File copy = File.createTempFile(Files.getNameWithoutExtension(libraryPath) + "_" + ++numClasps, "." + Files.getFileExtension(libraryPath));
            Files.copy(origFile, copy);
            return (Clasp3Library) Native.loadLibrary(copy.getPath(), Clasp3Library.class, NativeUtils.NATIVE_OPTIONS);
        } catch (IOException e) {
            throw new RuntimeException("Couldn't create a copy of clasp!!!");
        }
    }
}
