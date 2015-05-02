package ca.ubc.cs.beta.stationpacking.test;

import com.google.common.io.Resources;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Contains static helper methods for handling paths to resource files stored within the project hierarchy.
 * @author pcernek
 */
public class SATFCPaths {

    /**
     * Convert a string, specifying a path to a resource file, into a java Path object, suited to file I/O.
     *  The path is assumed to be relative to the "resources" folder.
     *  highest directory
     * @param resourceLocationString - the relative path to a resource contained in a resource folder.
     * @return - a Path object corresponding to the location of that resource file.
     * @throws URISyntaxException - if the string passed as an argument cannot be parsed as a valid path.
     */
    public static Path resourceLocationToPath(String resourceLocationString) throws URISyntaxException {
        return Paths.get(Resources.getResource(resourceLocationString).toURI());
    }

}
