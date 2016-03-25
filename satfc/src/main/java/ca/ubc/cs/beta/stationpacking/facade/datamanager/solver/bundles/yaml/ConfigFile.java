package ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.yaml;

import java.io.File;
import java.io.IOException;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.io.Resources;

import lombok.Data;

/**
 * A YAML config file.
 * Can either be internal (bundled inside the jar) or external (from the file system)
 */
@Data
public class ConfigFile {

    final String fileName;
    final boolean internal;

    public String getFileAsString() {
        try {
            if (internal) {
                return Resources.toString(Resources.getResource("bundles" + File.separator + fileName + ".yaml"), Charsets.UTF_8);
            } else {
                return Files.toString(new File(fileName), Charsets.UTF_8);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not load in config file", e);
        }
    }

}
