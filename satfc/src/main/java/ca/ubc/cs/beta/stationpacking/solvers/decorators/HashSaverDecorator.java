package ca.ubc.cs.beta.stationpacking.solvers.decorators;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.cache.StationPackingInstanceHasher;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * Created by newmanne on 12/11/15.
 * Save the hash of the instance
 */
@Slf4j
public class HashSaverDecorator extends ASolverDecorator {

    private final File fResultFile;

    public HashSaverDecorator(ISolver aSolver, @NonNull String aResultFile) {
        super(aSolver);
        File resultFile = new File(aResultFile);

        if (resultFile.exists()) {
            throw new IllegalArgumentException("Result file " + aResultFile + " already exists.");
        }

        fResultFile = resultFile;
    }

    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
        String instanceName = aInstance.getName();
        String hash = StationPackingInstanceHasher.hash(aInstance).toString();
        String line = instanceName.replace(".srpk", "") + "," + hash;
        try {
            Files.append(line + System.lineSeparator(), fResultFile, Charsets.UTF_8);
        } catch (IOException e) {
            log.error("Couldn't save to file " + fResultFile.getAbsolutePath(), e);
        }
        return fDecoratedSolver.solve(aInstance, aTerminationCriterion, aSeed);
    }

}
