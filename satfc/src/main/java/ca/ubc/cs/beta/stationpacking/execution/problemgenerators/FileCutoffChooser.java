package ca.ubc.cs.beta.stationpacking.execution.problemgenerators;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import au.com.bytecode.opencsv.CSVReader;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by newmanne on 2016-02-16.
 */
@Slf4j
public class FileCutoffChooser implements ICutoffChooser {

    private final Map<String, Double> nameToRuntime;
    private final double defaultCutoff;

    public FileCutoffChooser(String cutoffFile, double defaultCutoff) throws FileNotFoundException {
        this.defaultCutoff = defaultCutoff;
        nameToRuntime = new HashMap<>();
        final CSVReader csvReader = new CSVReader(new FileReader(cutoffFile), ',');
        String[] line = null;
        try {
            while ((line = csvReader.readNext()) != null) {
                String instanceName = line[0];
                double cutoff = Double.parseDouble(line[1]);
                nameToRuntime.put(instanceName, cutoff);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not parse line " + Arrays.toString(line), e);
        }
    }

    @Override
    public double getCutoff(SATFCFacadeProblem problem) {
        return nameToRuntime.getOrDefault(problem.getInstanceName(), defaultCutoff);
    }

}
