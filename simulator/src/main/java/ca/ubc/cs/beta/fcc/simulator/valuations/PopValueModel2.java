package ca.ubc.cs.beta.fcc.simulator.valuations;

import ca.ubc.cs.beta.fcc.simulator.station.IStationDB;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.station.Nationality;
import ca.ubc.cs.beta.fcc.simulator.utils.SimulatorUtils;
import lombok.extern.slf4j.Slf4j;
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

    final private double C = 8.698140284166147;
    final private double A = -1.1087198440401997;
    private final boolean useLeftTail;

    private Map<IStationInfo, MaxCFStickValues.IValueGenerator> stationToGenerator;

    public Map<IStationInfo, MaxCFStickValues.IValueGenerator> get() {
        return stationToGenerator;
    }

//            def inv(y, loc, scale, shape):
//                return loc + (scale / shape) * ((1/(1-y))**shape - 1)
//            Scale: 3.2427660969468493 Shape: 9.178023484452517
    private double invertParetoCDF(double y, double loc, double scale, double shape) {
        return loc + (scale / shape) * (Math.pow((1. / (1. - y)), shape) - 1);
    }

    private double sample() {
        double sample = random.nextDouble();
        if (useLeftTail && sample <= 0.15) {
            return invertParetoCDF(sample, 0.15, 3.2427660969468493, 9.178023484452517);
        }
        return Math.exp(sample * (C - A) + A);
    }

    public PopValueModel2(RandomGenerator random, IStationDB stationDB, boolean useLeftTail) {
        this.random = random;
        this.useLeftTail = useLeftTail;
        if (this.useLeftTail) {
            log.info("Using pareto left tail!");
        }
        stationToGenerator = new HashMap<>();
        for (IStationInfo station : stationDB.getStations(Nationality.US)) {
            final MaxCFStickValues.IValueGenerator valueGenerator = () -> (long) (station.getPopulation() * sample());
            stationToGenerator.put(station, valueGenerator);
        }
    }

}