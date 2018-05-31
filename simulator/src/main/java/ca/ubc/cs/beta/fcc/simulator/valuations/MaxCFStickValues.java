package ca.ubc.cs.beta.fcc.simulator.valuations;

import ca.ubc.cs.beta.fcc.simulator.station.IStationDB;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.SimulatorUtils;
import com.google.common.base.Preconditions;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.math.util.FastMath;
import org.apache.commons.math3.distribution.NormalDistribution;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Created by newmanne on 2016-11-03.
 */
@Slf4j
public class MaxCFStickValues {

    public interface IValueGenerator {

        double generateValue();

    }

    final String FAC_ID = "FacID";
    final String MEAN_CF = "MeanCF";
    final String MEAN_CF_MULTIPLES = "MeanCFMultiples";
    final String MEAN_LOG_STICK = "MeanLogStick";

    @Getter
    private final Random random;

    private Map<IStationInfo, IValueGenerator> stationToGenerator;

    public Map<IStationInfo, IValueGenerator> get() {
        return stationToGenerator;
    }

    public MaxCFStickValues(String csvFile, IStationDB.IModifiableStationDB stationDB, int valuesSeed) {
        log.info("Reading valuations from {}", csvFile);
        this.random = new Random(valuesSeed);
        stationToGenerator = new HashMap<>();
        final Iterable<CSVRecord> records = SimulatorUtils.readCSV(csvFile);
        for (CSVRecord record : records) {
            final int id = Integer.parseInt(record.get(FAC_ID));
            final double meanCF = Double.parseDouble(record.get(MEAN_CF));
            final double meanCFMultiples = Double.parseDouble(record.get(MEAN_CF_MULTIPLES));
            final double meanLogStick = Double.parseDouble(record.get(MEAN_LOG_STICK));
            final IStationInfo station = stationDB.getStationById(id);
            if (station != null) {
                // It is possible to be null here when Ulrich provides a value for a station that was not offered an opening price and is therefore not eligible
                final ValueGenerator valueGenerator = new ValueGenerator(random, station, meanCF, meanCFMultiples, meanLogStick);
                stationToGenerator.put(station, valueGenerator);
            }
        }
    }

    /**
     * See Doraszelski, Ulrich, et al. "Ownership Concentration and Strategic Supply Reduction." (2016).
     */
    public static class ValueGenerator implements IValueGenerator {

        // Might be better to read from CSV...
        public static final double STD_CASH_FLOW = 1.029461;
        public static final double STD_CASH_FLOW_MULTIPLE = 0.976377254629814;
        public static final double STD_LOG_STICK = 0.444053814276261;

        NormalDistribution cashFlow;
        NormalDistribution cashFlowMultiple;
        NormalDistribution logStick;

        Random random;

        IStationInfo stationInfo;

        public ValueGenerator(Random random, @NonNull IStationInfo stationInfo, double meanCF, double meanCFMultiple, double meanLogStick) {
            cashFlow = new NormalDistribution(meanCF, STD_CASH_FLOW);
            cashFlowMultiple = new NormalDistribution(meanCFMultiple, STD_CASH_FLOW_MULTIPLE);
            logStick = new NormalDistribution(meanLogStick, STD_LOG_STICK);
            this.random = random;
            this.stationInfo = stationInfo;
        }

        @Override
        public double generateValue() {
            final double cf = cashFlow.inverseCumulativeProbability(random.nextDouble());
            final double cfMultiple = cashFlowMultiple.inverseCumulativeProbability(random.nextDouble());
            final double cfVal = cf * cfMultiple;
            Preconditions.checkState(cfMultiple > 0 || cf > 0, "Neither CF nor CF multiple was positive!");

            final double logStickVal = logStick.inverseCumulativeProbability(random.nextDouble());

            final int pop = stationInfo.getPopulation();
            final double stickVal = (FastMath.exp(logStickVal) * 6 * pop) / 1e6;

            return Math.max(cfVal, stickVal) * 1e6;
        }

    }

}
