package ca.ubc.cs.beta.fcc.simulator.utils;

import ca.ubc.cs.beta.fcc.simulator.ladder.ILadder;
import ca.ubc.cs.beta.fcc.simulator.parameters.MultiBandSimulatorParameters;
import ca.ubc.cs.beta.fcc.simulator.parameters.SimulatorParameters;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SimulatorResult;
import ca.ubc.cs.beta.fcc.simulator.station.*;
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
import org.apache.commons.math.util.FastMath;
import org.apache.commons.math.util.MathUtils;
import org.apache.commons.math3.distribution.LogNormalDistribution;
import org.apache.commons.math3.random.JDKRandomGenerator;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
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
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

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

    public static long benchmarkToActualPrice(IStationInfo station, Band band, Map<Band, Double> benchmarkPrices) {
        final double benchmarkHome = benchmarkPrices.get(station.getHomeBand());
        // Second arg to min is about splitting the cost of a UHF station going to your spot and you going elsewhere
        final double nonVolumeWeightedActual = max(0, min(benchmarkPrices.get(Band.OFF), benchmarkPrices.get(band) - benchmarkHome));
        // Price offers are rounded down to nearest integer
        return (long) Math.floor(station.getVolume() * nonVolumeWeightedActual);
    }


    public static Map<Band, Long> createValueMap(long UHFPrice, IStationInfo station, RandomGenerator random, double noiseStd, double HVHF, double LVHF) {
        Preconditions.checkArgument(UHFPrice > 0, "Negative UHF Price! %s", UHFPrice);
        Preconditions.checkArgument(station.getPopulation() > 0, "Zero population station! %s", station);

        long originalPrice = UHFPrice;
        UHFPrice = toNearestThousand((long) MathUtils.round(UHFPrice, -3));
        if (UHFPrice % 1000 != 0) {
            throw new IllegalStateException("Value is not a clean multiple of 1000");
        }

        if (UHFPrice < 3000) {
            log.warn("UHF Price for {} rounds to {} in the nearest thousand ({}). Hard for this value model to work that way because of the 2/3, 1/3... Also suspiciously low. Changing to 3k", station, UHFPrice, originalPrice);
            UHFPrice = 3000;
        }

        // Take care that stations MUST value higher bands more for this to make any sense
        final Map<Band, Long> valueMap = new HashMap<>();

        // UHF Value
        if (station.getHomeBand().equals(Band.UHF)) {
            valueMap.put(Band.UHF, UHFPrice);
        }

        // HVHF Value
        if (station.getHomeBand().isAbove(Band.LVHF)) {
            long HVHFValue;
            do {
                HVHFValue = noisyValue(UHFPrice, random, HVHF, noiseStd);
//                log.info("HVHF {} {} {}", station, HVHFValue, UHFPrice);
            }
            while (HVHFValue <= 0 || HVHFValue >= UHFPrice); // Should not be negative, should not be bigger than UHF price. Remember that normals are unbounded...
            valueMap.put(Band.HVHF, HVHFValue);
        }

        // LVHF Value
        long LVHFValue;
        do {
            LVHFValue = noisyValue(UHFPrice, random, LVHF, noiseStd);
//            log.info("LVHF {} {} {}", station, valueMap.getOrDefault(Band.HVHF, (long) Math.floor(UHFPrice)), LVHFValue);
        } while (LVHFValue <= 0 || (LVHFValue >= valueMap.getOrDefault(Band.HVHF, Long.MAX_VALUE) || LVHFValue >= valueMap.getOrDefault(Band.UHF, Long.MAX_VALUE)));
        valueMap.put(Band.LVHF, LVHFValue);

        valueMap.put(Band.OFF, 0L);
        checkValuesOrdered(station.getId(), valueMap);
        return valueMap;
    }

    private static long toNearestThousand(long unrounded) {
        return (unrounded + 500) / 1000 * 1000;
    }


    private static long noisyValue(double UHFPrice, RandomGenerator random, double frac, double noiseStd) {
        double noise = random.nextGaussian() * noiseStd + 1;
        long longValue = (long) MathUtils.round(UHFPrice * frac * noise, -3);
        // stupid floating point means this might not actually be rounded properly
        return toNearestThousand(longValue);
    }

    public static void assignValues(SimulatorParameters parameters) {
        final RandomGenerator random = parameters.getValuesGenerator();

        log.info("Assigning valuations to stations");
        final IStationDB.IModifiableStationDB stationDB = parameters.getStationDB();
        final Collection<IStationInfo> americanStations = stationDB.getStations(Nationality.US);
        final Set<IStationInfo> stationsRemainingWithoutValues = new HashSet<>(americanStations);

        // To ensure consistent ordering (for random seed purposes), process by Band, ID.
        Comparator<IStationInfo> consistentOrdering = (a, b) -> ComparisonChain.start().compare(a.getHomeBand(), b.getHomeBand()).compare(a.getId(), b.getId()).result();
        Comparator<Map.Entry<IStationInfo, MaxCFStickValues.IValueGenerator>> consistentOrderingEntry = (a, b) -> consistentOrdering.compare(a.getKey(), b.getKey());

        final boolean historic = parameters.getParticipationModel().equals(SimulatorParameters.ParticipationModel.HISTORICAL_DATA);

        if (parameters.getValueFile() != null) {
            log.info("Reading station values from {}", parameters.getValueFile());
            final Iterable<CSVRecord> records = SimulatorUtils.readCSV(parameters.getValueFile());
            for (CSVRecord record : records) {
                final int id = Integer.parseInt(record.get("FacID"));
                final IStationInfo station = stationDB.getStationById(id);
                Preconditions.checkState(station.getNationality().equals(Nationality.US), "Station %s is not a US station! Only US stations should have values", station);
                final String UHFValueString = record.get("UHFValue");
                final String HVHFValueString = record.get("HVHFValue");
                final String LVHFValueString = record.get("LVHFValue");

                final Map<Band, Long> valueMap = new HashMap<>();
                valueMap.put(Band.OFF, 0L); // Do this explicitly to not have any floating point nonsense
                ((IModifiableStationInfo) station).setValues(valueMap);
                if (station.getHomeBand().isAboveOrEqualTo(Band.UHF)) {
                    Preconditions.checkNotNull(UHFValueString, "UHF value cannot be null for station %s", station);
                    valueMap.put(Band.UHF, Long.parseLong(UHFValueString));
                }
                if (station.getHomeBand().isAboveOrEqualTo(Band.HVHF)) {
                    Preconditions.checkNotNull(HVHFValueString, "HVHF value cannot be null for station %s", station);
                    valueMap.put(Band.HVHF, Long.parseLong(HVHFValueString));
                }
                if (station.getHomeBand().isAboveOrEqualTo(Band.LVHF)) {
                    Preconditions.checkNotNull(LVHFValueString, "LVHF value cannot be null for station %s", station);
                    valueMap.put(Band.LVHF, Long.parseLong(LVHFValueString));
                }

                checkValuesOrdered(id, valueMap);

                stationsRemainingWithoutValues.remove(station);
                log.info("Value of {} for {} in most valued band", Humanize.spellBigNumber(valueMap.values().stream().max(Double::compare).get()), id);
            }
        } else if (parameters.isPopValues()) {
            log.info("Using population model values");
            final Map<IStationInfo, MaxCFStickValues.IValueGenerator> stationToGenerator = parameters.getPopValueModel().get();
            for (final Map.Entry<IStationInfo, MaxCFStickValues.IValueGenerator> entry : stationToGenerator.entrySet().stream().sorted(consistentOrderingEntry).collect(Collectors.toList())) {
                final IStationInfo station = entry.getKey();
                double value;
                Map<Band, Long> valueMap;
                do {
                    value = entry.getValue().generateValue();
                    valueMap = createValueMap((long) value, station, random, parameters.getNoiseStd(), parameters.getHVHFFrac(), parameters.getLVHFFrac());
                } while (historic &&
                        parameters.getHistoricData().getHistoricalStations().contains(station) &&
                        valueMap.get(station.getHomeBand()) > parameters.getHistoricData().getHistoricalOpeningPrices().get(station.getId()));

                ((IModifiableStationInfo) station).setValues(valueMap);
                stationsRemainingWithoutValues.remove(station);
            }
        } else {
            // TODO: Better way to sample these...
//            final List<List<Object>> values = new ArrayList<>();
//            int QMAX = 10000;
//            for (int q = 0; q < QMAX; q++) {
//                values.add(new ArrayList<>());

            final Map<String, DescriptiveStatistics> dmaToUHFPricePerPop = new HashMap<>();
            final Map<IStationInfo, MaxCFStickValues.IValueGenerator> stationToGenerator;

            log.info("Using max(CF, stick)-based values");
            final MaxCFStickValues maxCFStickValues = new MaxCFStickValues(parameters.getValuesGenerator(), parameters.getMaxCFStickFile(), stationDB, parameters.getValuesSeed());
            stationToGenerator = maxCFStickValues.get();


            // First, process the stations with a value mode (ensure a consistent ordering)
            for (final Map.Entry<IStationInfo, MaxCFStickValues.IValueGenerator> entry : stationToGenerator.entrySet().stream().sorted(consistentOrderingEntry).collect(Collectors.toList())) {
                final IStationInfo station = entry.getKey();
                if (station.getHomeBand().isVHF()) {
                    log.warn("Value model for non-UHF station {} (maybe reclassified?) Skipping for now!", station);
                    continue;
                }

                long value;
                do {
                    value = entry.getValue().generateValue();
                } while ((value < 0) ||
                        (historic &&
                                parameters.getHistoricData().getHistoricalStations().contains(station) &&
                                value > parameters.getHistoricData().getHistoricalOpeningPrices().get(station.getId()))); // If historic, we are conditioning on your home band value being lower than your off air opening price. So keep resampling until you sample a price at which participation would have occurred

                dmaToUHFPricePerPop.putIfAbsent(station.getDMA(), new DescriptiveStatistics());


                double logValuePerPop = FastMath.log((double) value / station.getPopulation());
                if (!Double.isFinite(logValuePerPop)) {
                    throw new IllegalStateException("Non-finite value per pop? value, pop " + value + " " + station.getPopulation());
                }
                dmaToUHFPricePerPop.get(station.getDMA()).addValue(logValuePerPop);
                final Map<Band, Long> valueMap = createValueMap(value, station, random, parameters.getNoiseStd(), parameters.getHVHFFrac(), parameters.getLVHFFrac());
                ((IModifiableStationInfo) station).setValues(valueMap);
                stationsRemainingWithoutValues.remove(station);
            }

            final DescriptiveStatistics nationalStats = new DescriptiveStatistics();
            for (DescriptiveStatistics ds : dmaToUHFPricePerPop.values()) {
                for (double v : ds.getValues()) {
                    nationalStats.addValue(v);
                }
            }


            if (parameters.isInferValues()) {
                int nationalCount = 0;
                final Set<String> noDataDma = new HashSet<>();
                final ArrayListMultimap<Band, IStationInfo> stationTypes = ArrayListMultimap.create();
                // Next, process stations without a model
                final List<IStationInfo> stillToBeValued = americanStations.stream().filter(station -> station.getValues() == null).sorted(consistentOrdering).collect(Collectors.toList());
                log.info("Inferring values for remaining {} stations based on average price per pop in DMA. By band: {}", stillToBeValued.size(), stillToBeValued.stream().collect(groupingBy(IStationInfo::getHomeBand, counting())));
                for (final IStationInfo station : stillToBeValued) {
                    stationTypes.put(station.getHomeBand(), station);
                    DescriptiveStatistics stats = dmaToUHFPricePerPop.get(station.getDMA());
                    if (stats == null || stats.getN() == 0 || stats.getStandardDeviation() == 0) {
                        nationalCount++;
                        noDataDma.add(station.getDMA());
                        stats = nationalStats;
                    }

                    final LogNormalDistribution distribution = new LogNormalDistribution(random, stats.getMean(), stats.getStandardDeviation());
                    final Integer pOpenHistoric = parameters.getHistoricData().getHistoricalOpeningPrices().get(station.getId());
                    double sample;
                    Map<Band, Long> valueMap;
                    do {
                        sample = distribution.sample();
                        if (!(sample > 0)) {
                            log.info(stats.toString());
                            throw new IllegalStateException("Sample is " + sample);
                        }
                        valueMap = createValueMap((long) (sample * station.getPopulation()), station, random, parameters.getNoiseStd(), parameters.getHVHFFrac(), parameters.getLVHFFrac());
                    } while (historic &&
                            parameters.getHistoricData().getHistoricalStations().contains(station) &&
                            valueMap.get(station.getHomeBand()) > pOpenHistoric);
                    ((IModifiableStationInfo) station).setValues(valueMap);
                    stationsRemainingWithoutValues.remove(station);
                }
                log.info("Not enough info available for {} DMAs. Used national data for {} stations (which may yet be removed)", noDataDma.size(), nationalCount);
            }

//                final int z = q;
//                stationDB.getStations().stream()
//                        .sorted(consistentOrdering)
//                        .filter(s -> s.getValues() != null)
//                        .forEach(s -> {
//                            values.get(z).add(s.getValue());
//                            if (z != QMAX - 1) {
//                                ((StationInfo) s).setValues(null);
//                            }
//                        });
//            }
//            final List<String> headers = stationDB.getStations().stream()
//                    .sorted(consistentOrdering)
//                    .filter(s -> s.getValues() != null)
//                    .map(s -> String.valueOf(s.getId())).collect(Collectors.toList());
//            final String valueFileName = parameters.getOutputFolder() + File.separator + "value_samples.csv";
//
//            SimulatorUtils.toCSV(valueFileName, headers, values);
//
        }

        for (IStationInfo toRemove : stationsRemainingWithoutValues) {
            stationDB.removeStation(toRemove.getId());
        }


        final Map<Band, List<IStationInfo>> droppedStationToBand = stationsRemainingWithoutValues.stream().collect(groupingBy(IStationInfo::getHomeBand));
        for (Map.Entry<Band, List<IStationInfo>> entry : droppedStationToBand.entrySet()) {
            log.info("Dropped {} {} stations due to not having valuations", entry.getValue().size(), entry.getKey());
        }

        if (historic) {
            for (IStationInfo s : parameters.getHistoricData().getHistoricalStations()) {
                if (s.isMainland()) {
                    if (!stationDB.getStations().contains(s)) {
                        throw new IllegalStateException(String.format("You didn't include %s when it should have participated!!!", s));
                    }
                }
            }
        }

        // Create a  values.csv file
        final String valueFileName = parameters.getOutputFolder() + File.separator + "values.csv";
        List<List<Object>> records = stationDB.getStations().stream()
                .sorted(consistentOrdering)
                .filter(s -> s.getValues() != null)
                .map(s -> Lists.<Object>newArrayList(s.getId(), s.getValues().get(Band.UHF), s.getValues().get(Band.HVHF), s.getValues().get(Band.LVHF))).collect(Collectors.toList());
        SimulatorUtils.toCSV(valueFileName, Lists.newArrayList("FacID", "UHFValue", "HVHFValue", "LVHFValue"), records);
    }

    private static void checkValuesOrdered(int id, Map<Band, Long> valueMap) {
        final List<Long> valuesSortedByBand = valueMap.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(Map.Entry::getValue).collect(Collectors.toList());
        if (!Ordering.natural().isStrictlyOrdered(valuesSortedByBand)) {
            throw new IllegalStateException(String.format("Station %s does not value its bands UHF > HVHF > LVHF > OFF. This can lead to strange behaviour and is probably a mistake. %s", id, valueMap.toString()));
        }
    }

    public static void adjustCTSimple(int ct, IStationDB.IModifiableStationDB stationDB) {
        // "Apply" the new clearing target to anything that was maintaining max channel state
        // WARNING: This can lead to a lot of strange bugs if something queries a station's domain and stores it before CT is finalized...
        log.info("Setting max channel to {}", ct);
        BandHelper.setUHFChannels(ct);
        for (IStationInfo s : stationDB.getStations()) {
            ((IModifiableStationInfo) s).setMaxChannel(s.getNationality().equals(Nationality.CA) ? ct - 1 : ct);
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