package ca.ubc.cs.beta.fcc.simulator.utils;

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
import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import humanize.Humanize;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.math3.distribution.LogNormalDistribution;
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

    public static final ImmutableList<Integer> CLEARING_TARGETS = ImmutableList.of(29, 31, 32, 36, 38, 39, 41, 43, 44);

    public static Optional<Integer> getNextTarget(int prevTarget) {
        return CLEARING_TARGETS.stream().filter(c -> c > prevTarget).findFirst();
    }

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


    public static Map<Band, Double> createValueMap(double UHFPrice, IStationInfo station, Random random, double noiseStd) {
        // Take care that stations MUST value higher bands more for this to make any sense
        final Map<Band, Double> valueMap = new HashMap<>();

        // UHF Value
        if (station.getHomeBand().equals(Band.UHF)) {
            valueMap.put(Band.UHF, UHFPrice);
        }

        // HVHF Value
        if (station.getHomeBand().isAbove(Band.LVHF)) {
            double HVHFValue;
            do {
                HVHFValue = noisyValue(UHFPrice, random, 2. / 3, noiseStd);
            }
            while (HVHFValue <= 0 || HVHFValue >= UHFPrice); // Should not be negative, should not be bigger than UHF price. Remember that normals are unbounded...
            valueMap.put(Band.HVHF, HVHFValue);
        }

        // LVHF Value
        double LVHFValue;
        do {
            LVHFValue = noisyValue(UHFPrice, random, 1. / 3, noiseStd);
        } while (LVHFValue <= 0 || (LVHFValue >= valueMap.getOrDefault(Band.HVHF, UHFPrice)));
        valueMap.put(Band.LVHF, LVHFValue);

        valueMap.put(Band.OFF, 0.);
        checkValuesOrdered(station.getId(), valueMap);
        return valueMap;
    }

    private static double noisyValue(double UHFPrice, Random random, double frac, double noiseStd) {
        double noise = random.nextGaussian() * noiseStd + 1;
        return UHFPrice * frac * noise;
    }

    public static void assignValues(MultiBandSimulatorParameters parameters) {
        log.info("Assigning valuations to stations");
        final IStationDB.IModifiableStationDB stationDB = parameters.getStationDB();

        final Collection<IStationInfo> americanStations = stationDB.getStations(Nationality.US);
        final Set<IStationInfo> removed = new HashSet<>();
        removed.addAll(americanStations);

        // To ensure consistent ordering, process by Band, ID.
        Comparator<IStationInfo> consistentOrdering = (a, b) -> ComparisonChain.start().compare(a.getHomeBand(), b.getHomeBand()).compare(a.getId(), b.getId()).result();
        Comparator<Map.Entry<IStationInfo, MaxCFStickValues.IValueGenerator>> consistentOrderingEntry = (a, b) -> consistentOrdering.compare(a.getKey(), b.getKey());

        if (parameters.isPopValues()) {
            log.info("Using population-based values");
            final Random random = new Random(parameters.getValuesSeed());
            final LogNormalDistribution stickValuePerMhzPopDistribution = new LogNormalDistribution(-1.4, 0.5);

            for (final IStationInfo station : americanStations.stream().sorted(consistentOrdering).collect(Collectors.toList())) {
                final double stickValuePerMhzPop = stickValuePerMhzPopDistribution.inverseCumulativeProbability(random.nextDouble());
                final double uhfValue = stickValuePerMhzPop * station.getPopulation() * 6; // 6 Mhz licenses
                final Map<Band, Double> valueMap = createValueMap(uhfValue, station, random, parameters.getNoiseStd());
                ((StationInfo) station).setValues(valueMap);
                removed.remove(station);
            }
        } else if (parameters.getMaxCFStickFile() != null) {
            log.info("Using max(CF, stick)-based values");

            final ArrayListMultimap<String, Double> dmaToPricePerPop = ArrayListMultimap.create();

            final MaxCFStickValues maxCFStickValues = new MaxCFStickValues(parameters.getMaxCFStickFile(), stationDB, parameters.getValuesSeed());
            final Random random = maxCFStickValues.getRandom();
            final Map<IStationInfo, MaxCFStickValues.IValueGenerator> stationToGenerator = maxCFStickValues.get();
            // First, process the stations with a value mode (ensure a consistent ordering)
            for (final Map.Entry<IStationInfo, MaxCFStickValues.IValueGenerator> entry : stationToGenerator.entrySet().stream().sorted(consistentOrderingEntry).collect(Collectors.toList())) {
                final IStationInfo station = entry.getKey();
                if (station.getHomeBand().isVHF()) {
                    log.warn("Value model for non-UHF station {} (maybe reclassified?) Skipping for now!", station);
                    continue;
                }
                double value = entry.getValue().generateValue();
                dmaToPricePerPop.put(station.getDMA(), value / station.getPopulation());
                final Map<Band, Double> valueMap = createValueMap(value, station, random, parameters.getNoiseStd());
                ((StationInfo) station).setValues(valueMap);
                removed.remove(station);
            }

            // Get the mean price per DMA for the stations we do know about
            final Map<String, Double> meanPricePerDMA = new HashMap<>();
            for (String dma : dmaToPricePerPop.keySet()) {
                meanPricePerDMA.put(dma, dmaToPricePerPop.get(dma).stream().collect(Collectors.averagingDouble(d -> d)));
            }
            final double nationalMeanDMAPricePerPop = dmaToPricePerPop.values().stream().mapToDouble(d -> d).average().getAsDouble();

            if (parameters.isInferValues()) {
                log.info("Inferring values for remaining stations based on mean price per pop in DMA");
                // Next, process stations without a model
                americanStations.stream().filter(station -> station.getValues() == null).sorted(consistentOrdering).forEach(station -> {
                    final double meanDMAPricePerPop = meanPricePerDMA.getOrDefault(station.getDMA(), nationalMeanDMAPricePerPop);
                    if (meanPricePerDMA.get(station.getDMA()) == null) {
                        log.debug("Mean DMA not available for DMA {}. Using average price nationally", station.getDMA());
                    }
                    final Map<Band, Double> valueMap = createValueMap(meanDMAPricePerPop * station.getPopulation(), station, random, parameters.getNoiseStd());
                    ((StationInfo) station).setValues(valueMap);
                });
            }
        } else if (parameters.getValueFile() != null) {
            log.info("Reading station values from {}", parameters.getValueFile());
            final Iterable<CSVRecord> records = SimulatorUtils.readCSV(parameters.getValueFile());
            for (CSVRecord record : records) {
                final int id = Integer.parseInt(record.get("FacID"));
                final IStationInfo station = stationDB.getStationById(id);
                Preconditions.checkState(station.getNationality().equals(Nationality.US), "Station %s is not a US station! Only US stations should have values", station);
                final String UHFValueString = record.get("UHFValue");
                final String HVHFValueString = record.get("HVHFValue");
                final String LVHFValueString = record.get("LVHFValue");

                final Map<Band, Double> valueMap = new HashMap<>();
                valueMap.put(Band.OFF, 0.); // Do this explicitly to not have any floating point nonsense
                ((StationInfo) station).setValues(valueMap);
                if (station.getHomeBand().isAboveOrEqualTo(Band.UHF)) {
                    Preconditions.checkNotNull(UHFValueString, "UHF value cannot be null for station %s", station);
                    valueMap.put(Band.UHF, Double.parseDouble(UHFValueString));
                }
                if (station.getHomeBand().isAboveOrEqualTo(Band.HVHF)) {
                    Preconditions.checkNotNull(HVHFValueString, "HVHF value cannot be null for station %s", station);
                    valueMap.put(Band.HVHF, Double.parseDouble(HVHFValueString));
                }
                if (station.getHomeBand().isAboveOrEqualTo(Band.LVHF)) {
                    Preconditions.checkNotNull(LVHFValueString, "LVHF value cannot be null for station %s", station);
                    valueMap.put(Band.LVHF, Double.parseDouble(LVHFValueString));
                }

                checkValuesOrdered(id, valueMap);

                removed.remove(station);
                log.info("Value of {} for {} in most valued band", Humanize.spellBigNumber(valueMap.values().stream().max(Double::compare).get()), id);
            }
        } else {
            throw new IllegalStateException("No way to assign station values. Must specify value file with -VALUE-FILE");
        }


        for (IStationInfo toRemove : removed) {
            stationDB.removeStation(toRemove.getId());
        }

        final Map<Band, List<IStationInfo>> droppedStationToBand = removed.stream().collect(Collectors.groupingBy(IStationInfo::getHomeBand));
        for (Map.Entry<Band, List<IStationInfo>> entry : droppedStationToBand.entrySet()) {
            log.info("Dropped {} {} stations due to not having valuations", entry.getValue().size(), entry.getKey());
        }
    }

    private static void checkValuesOrdered(int id, Map<Band, Double> valueMap) {
        final List<Double> valuesSortedByBand = valueMap.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(Map.Entry::getValue).collect(Collectors.toList());
        if (!Ordering.natural().isStrictlyOrdered(valuesSortedByBand)) {
            throw new IllegalStateException(String.format("Station %s does not value its bands UHF > HVHF > LVHF > OFF. This can lead to strange behaviour and is probably a mistake. %s", id, valueMap.toString()));
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

        /**
         * Get CPU time in nanoseconds.
         */
        private long getCpuTime() {
            final ThreadMXBean bean = ManagementFactory.getThreadMXBean();
            return bean.isCurrentThreadCpuTimeSupported() ?
                    bean.getCurrentThreadCpuTime() : 0L;
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

        if (h == 0) {
            r = (m == 0) ? String.format("%d%s", sec, "s") :
                    (sec == 0) ? String.format("%d%s", m, "m") :
                            String.format("%d%s %d%s", m, "m", sec, "s");
        } else {
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
