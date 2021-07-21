package ca.ubc.cs.beta.fcc.simulator.valuations;

import ca.ubc.cs.beta.fcc.simulator.station.IStationDB;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.station.Nationality;
import ca.ubc.cs.beta.fcc.simulator.utils.SimulatorUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.map.HashedMap;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.math3.distribution.BinomialDistribution;
import org.apache.commons.math3.distribution.LogNormalDistribution;
import org.apache.commons.math3.random.RandomGenerator;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class PopValueModel2 {

    private final RandomGenerator random;
    private final boolean useRightTail;

    final private double C = 8.475590715290808;
    final private double A = -0.8726349972734521;
    private final boolean useLeftTail;

    private final double PL = 0.15;
    private final double PU = 0.7;

    private Map<IStationInfo, MaxCFStickValues.IValueGenerator> stationToGenerator;

    public static Map<IStationInfo, Double> stationToSample;

    public Map<IStationInfo, MaxCFStickValues.IValueGenerator> get() {
        return stationToGenerator;
    }

    private double invertFunLower(double y, double scale, double shape, double loc) {
        return (-scale/shape) * (Math.exp(-shape * Math.log(y/PL)) - 1) - loc;
    }

    private double invertFunUpper(double y, double scale, double shape, double loc) {
        return (scale * Math.pow((PU-1)/(y-1), shape) + shape * loc - scale) / shape;
    }


    private double sample(IStationInfo s) {
        double sample = random.nextDouble();
        stationToSample.put(s, sample);
        if (useLeftTail && sample <= PL) {
            // Use -QL here
            return invertFunLower(sample, 2.79350472460374, -1.6449304793139794, -1.6982509349240957);
        } else if (useRightTail && sample >= PU) {
            return invertFunUpper(sample, 217.383479182883, -0.3601907796110828, 290.3604265696093);
        }

        return Math.exp(sample * (C - A) + A);
    }

    public PopValueModel2(RandomGenerator random, IStationDB stationDB, boolean useLeftTail, boolean useRightTail) {
        this.random = random;
        this.useRightTail = useRightTail;
        stationToSample = new HashMap<>();
        this.useLeftTail = useLeftTail;
        if (this.useLeftTail) {
            log.info("Using pareto left tail!");
        }
        stationToGenerator = new HashMap<>();
        for (IStationInfo station : stationDB.getStations(Nationality.US)) {
            final MaxCFStickValues.IValueGenerator valueGenerator = () -> (long) (station.getPopulation() * sample(station));
            stationToGenerator.put(station, valueGenerator);
        }
    }

}