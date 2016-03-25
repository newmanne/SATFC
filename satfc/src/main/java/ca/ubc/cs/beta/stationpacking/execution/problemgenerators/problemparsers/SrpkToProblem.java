package ca.ubc.cs.beta.stationpacking.execution.problemgenerators.problemparsers;

import java.io.IOException;

import ca.ubc.cs.beta.stationpacking.execution.Converter;

/**
 * Created by newmanne on 2016-03-24.
 */
public class SrpkToProblem extends ANameToProblem {

    public SrpkToProblem(String interferencesFolder) {
        super(interferencesFolder);
    }

    @Override
    public Converter.StationPackingProblemSpecs getSpecs(String name) throws IOException {
        return Converter.StationPackingProblemSpecs.fromStationRepackingInstance(name);
    }

}
