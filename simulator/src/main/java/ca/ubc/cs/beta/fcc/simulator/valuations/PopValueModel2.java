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

    private Map<IStationInfo, MaxCFStickValues.IValueGenerator> stationToGenerator;

    public Map<IStationInfo, MaxCFStickValues.IValueGenerator> get() {
        return stationToGenerator;
    }

    private double sample() {
        return Math.exp(random.nextDouble() * (C - A) + A);
    }

    public PopValueModel2(RandomGenerator random, IStationDB stationDB) {
        this.random = random;
        stationToGenerator = new HashMap<>();
        for (IStationInfo station : stationDB.getStations(Nationality.US)) {
            final MaxCFStickValues.IValueGenerator valueGenerator = () -> station.getPopulation() * sample();
            stationToGenerator.put(station, valueGenerator);
        }
    }

}