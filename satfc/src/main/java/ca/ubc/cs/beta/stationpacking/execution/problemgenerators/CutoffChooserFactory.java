package ca.ubc.cs.beta.stationpacking.execution.problemgenerators;

import java.io.FileNotFoundException;

import ca.ubc.cs.beta.stationpacking.execution.parameters.SATFCFacadeParameters;

/**
 * Created by newmanne on 2016-02-16.
 */
public class CutoffChooserFactory {

    public static ICutoffChooser createFromParameters(SATFCFacadeParameters parameters) {
        if (parameters.fCutoffFile == null) {
            return problem -> parameters.fInstanceParameters.Cutoff;
        } else {
            try {
                return new FileCutoffChooser(parameters.fCutoffFile, parameters.fInstanceParameters.Cutoff);
            } catch (FileNotFoundException e) {
                throw new IllegalArgumentException("File not found " + parameters.fCutoffFile, e);
            }
        }
    }

}
