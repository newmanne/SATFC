package ca.ubc.cs.beta.stationpacking.execution.problemgenerators.problemparsers;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;

import org.apache.commons.io.FilenameUtils;

import com.google.common.collect.Sets;

import ca.ubc.cs.beta.stationpacking.execution.Converter;
import ca.ubc.cs.beta.stationpacking.execution.problemgenerators.SATFCFacadeProblem;

/**
 * Created by newmanne on 2016-03-24.
 */
public abstract class ANameToProblem implements IProblemParser {

    public ANameToProblem(String interferencesFolder) {
        this.interferencesFolder = interferencesFolder;
    }

    final String interferencesFolder;

    public abstract Converter.StationPackingProblemSpecs getSpecs(String name) throws IOException;

    @Override
    public SATFCFacadeProblem problemFromName(String name) throws IOException {
        String n = FilenameUtils.getBaseName(name);
        Converter.StationPackingProblemSpecs stationPackingProblemSpecs = getSpecs(name);
        return new SATFCFacadeProblem(
                stationPackingProblemSpecs.getDomains().keySet(),
                stationPackingProblemSpecs.getDomains().values().stream().reduce(new HashSet<>(), Sets::union),
                stationPackingProblemSpecs.getDomains(),
                stationPackingProblemSpecs.getPreviousAssignment(),
                interferencesFolder + File.separator + stationPackingProblemSpecs.getDataFoldername(),
                n
        );
    }

}
