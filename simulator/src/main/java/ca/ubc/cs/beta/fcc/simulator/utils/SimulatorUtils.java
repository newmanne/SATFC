package ca.ubc.cs.beta.fcc.simulator.utils;

import ca.ubc.cs.beta.fcc.simulator.MultiBandAuctioneer;
import ca.ubc.cs.beta.fcc.simulator.ladder.ILadder;
import ca.ubc.cs.beta.fcc.simulator.parameters.MultiBandSimulatorParameters;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SimulatorResult;
import ca.ubc.cs.beta.fcc.simulator.station.IStationDB;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.station.Nationality;
import ca.ubc.cs.beta.fcc.simulator.station.StationInfo;
import ca.ubc.cs.beta.fcc.simulator.valuations.MaxCFStickValues;
import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.ConstraintGrouper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableTable;
import humanize.Humanize;
import humanize.spi.context.ContextFactory;
import humanize.spi.context.DefaultContext;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.jgrapht.alg.NeighborIndex;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * Created by newmanne on 2016-05-20.
 */
@Slf4j
public class SimulatorUtils {

    public static final ImmutableList<Integer> CLEARING_TARGETS = ImmutableList.of(29,31,32,36,38,39,41,43,44);

    public static boolean isFeasible(SATFCResult result) {
        return result.getResult().equals(SATResult.SAT);
    }
    public static boolean isFeasible(@NonNull SimulatorResult result) {
        return isFeasible(result.getSATFCResult());
    }

    public static void toCSV(String filename, List<String> header, List<List<Object>> records) {
        FileWriter fileWriter = null;
        CSVPrinter csvPrinter = null;
        try {
            fileWriter = new FileWriter(filename);
            csvPrinter = CSVFormat.DEFAULT.withHeader(header.toArray(new String[header.size()])).print(fileWriter);
            csvPrinter.printRecords(records);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (fileWriter != null) {
                    fileWriter.flush();
                    fileWriter.close();
                }
                if (csvPrinter != null) {
                    csvPrinter.close();
                }
            } catch (IOException e) {
                log.error("Error in csv", e);
            }
        }
    }

    public static Iterable<CSVRecord> readCSV(FileReader in) {
        try {
            return CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(in);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public static Iterable<CSVRecord> readCSV(File file) {
        try {
            return readCSV(new FileReader(file));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static Iterable<CSVRecord> readCSV(String filename) {
        try {
            return readCSV(new FileReader(filename));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    // Returns a table mapping from station/band to neighbours using the definition of neighbour as anyone that might interfere with station in band
    public static ImmutableTable<IStationInfo, Band, Set<IStationInfo>> getBandNeighborIndexMap(@NonNull ILadder ladder, @NonNull IConstraintManager constraintManager) {
        final ImmutableTable.Builder<IStationInfo, Band, Set<IStationInfo>> builder = ImmutableTable.builder();
        for (Band band : ladder.getAirBands()) {
            final Map<Station, Set<Integer>> domains = ladder.getStations().stream()
                    .collect(Collectors.toMap(IStationInfo::toSATFCStation, s -> s.getDomain(band)));

            final Map<Station, IStationInfo> stationToInfo = ladder.getStations().stream().collect(Collectors.toMap(IStationInfo::toSATFCStation, Function.identity()));
            final SimpleGraph<IStationInfo, DefaultEdge> constraintGraph = ConstraintGrouper.getConstraintGraph(domains, constraintManager, stationToInfo);
            final NeighborIndex<IStationInfo, DefaultEdge> neighborIndex = new NeighborIndex<>(constraintGraph);
            for (IStationInfo s : stationToInfo.values()) {
                builder.put(s, band, neighborIndex.neighborsOf(s));
            }
        }
        return builder.build();
    }

    public static double benchmarkToActualPrice(IStationInfo station, Band band, Map<Band, Double> benchmarkPrices) {
        final double benchmarkHome = benchmarkPrices.get(station.getHomeBand());
        // Second arg to min is about splitting the cost of a UHF station going to your spot and you going elsewhere
        final double nonVolumeWeightedActual = max(0, min(benchmarkPrices.get(Band.OFF), benchmarkPrices.get(band) - benchmarkHome));
        // Price offers are rounded down to nearest integer
        return Math.floor(station.getVolume() * nonVolumeWeightedActual);
    }

    public static void assignValues(MultiBandSimulatorParameters parameters) {
        log.info("Assigning valuations to stations");
        final IStationDB.IModifiableStationDB stationDB = parameters.getStationDB();
        final MaxCFStickValues maxCFStickValues = new MaxCFStickValues(parameters.getMaxCFStickFile(), stationDB, parameters.getValuesSeed());
        final Map<IStationInfo, MaxCFStickValues.IValueGenerator> stationToGenerator = maxCFStickValues.get();
        final Collection<IStationInfo> americanStations = stationDB.getStations(Nationality.US);
        final Set<IStationInfo> removed = new HashSet<>();
        for (final IStationInfo station : americanStations) {
            final MaxCFStickValues.IValueGenerator iValueGenerator = stationToGenerator.get(station);
            if (iValueGenerator == null || !station.getHomeBand().equals(Band.UHF)) {
                if (iValueGenerator != null && !station.getHomeBand().equals(Band.UHF)) {
                    log.warn("Value model for non-UHF station {} (maybe reclassified?) Skipping for now! TODO: change this once you model VHF stations with values", station);
                } else {
                    log.info("No valuation model for station {}, skipping", station);
                }
                stationDB.removeStation(station.getId());
                removed.add(station);
                continue;
            }
            double value = iValueGenerator.generateValue();
            final Map<Band, Double> valueMap = new HashMap<>();
            double frac = 1.0;
            for (Band band : station.getHomeBand().getBandsBelowInclusive(false)) {
                if (band.equals(Band.OFF)) {
                    continue;
                }
                // UHF gets full value for home, 2/3 for HVHF, 1/3 for LVHF, then 0
                valueMap.put(band, value * frac);
                frac -= 1. / 3;
            }
            valueMap.put(Band.OFF, 0.); // Do this explicitly to not have any floating point nonsense
            ((StationInfo) station).setValues(valueMap);
        }
        final Map<Band, List<IStationInfo>> droppedStationToBand = removed.stream().collect(Collectors.groupingBy(IStationInfo::getHomeBand));
        for (Map.Entry<Band, List<IStationInfo>> entry : droppedStationToBand.entrySet()) {
            log.info("Dropped {} {} stations due to not having valuations", entry.getValue().size(), entry.getKey());
        }
    }

    /**
     * Measure CPU Time for a SINGLE THREAD
     */
    public static class CPUTimeWatch {

        long startTime;

        public CPUTimeWatch() {
            this.startTime = getCpuTime();
        }

        public double getElapsedTime() {
            return (getCpuTime() - startTime) / 1e9;
        }

        /** Get CPU time in nanoseconds. */
        private long getCpuTime( ) {
            final ThreadMXBean bean = ManagementFactory.getThreadMXBean();
            return bean.isCurrentThreadCpuTimeSupported( ) ?
                    bean.getCurrentThreadCpuTime( ) : 0L;
        }

        public static CPUTimeWatch constructAutoStartWatch() {
            return new CPUTimeWatch();
        }

    }

    public static String duration(Number seconds) {
        // Humanize has a stupid bug in 1.2.2 that breaks after hours exceeds 60. Wait for new version. Until then...
        int s = seconds.intValue();
        boolean neg = s < 0;
        s = Math.abs(s);
        int h = (s / 3600);
        int m = (s / 60) % 60;
        int sec = s % 60;

        String r;

        if (h == 0)
        {
            r = (m == 0) ? String.format("%d%s", sec, "s") :
                    (sec == 0) ? String.format("%d%s", m, "m") :
                            String.format("%d%s %d%s", m, "m", sec, "s");
        } else
        {
            r = (m == 0) ?
                    ((sec == 0) ? String.format("%d%s", h, "h") :
                            String.format("%d%s %d%s", h, "h", sec, "s")) :
                    (sec == 0) ?
                            String.format("%d%s %d%s", h, "h", m, "m") :
                            String.format("%d%s %d%s %d%s", h, "h", m, "m", sec,
                                    "s");
        }

        return (neg ? '-' : "") + r;
    }

}
