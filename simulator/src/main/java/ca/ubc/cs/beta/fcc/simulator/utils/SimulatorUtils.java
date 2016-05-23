package ca.ubc.cs.beta.fcc.simulator.utils;

import ca.ubc.cs.beta.fcc.simulator.station.StationInfo;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by newmanne on 2016-05-20.
 */
public class SimulatorUtils {

    public static boolean isFeasible(SATFCResult result) {
        return result.getResult().equals(SATResult.SAT);
    }

    public static Set<Integer> toID(Collection<StationInfo> stationInfos) {
        return stationInfos.stream().map(StationInfo::getId).collect(Collectors.toSet());
    }

    public static void toCSV(String filename, List<String> header, List<List<Object>> records) {
        try {
            final FileWriter fileWriter = new FileWriter(filename);
            final CSVPrinter csvPrinter = new CSVPrinter(fileWriter, CSVFormat.DEFAULT);
            csvPrinter.printRecord(header);
            for (List<Object> record : records) {
                csvPrinter.printRecord(record);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
