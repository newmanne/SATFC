package ca.ubc.cs.beta.fcc.simulator.valuations;

import ca.ubc.cs.beta.fcc.simulator.station.IStationDB;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
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
public class PopValueModel {

    final String FAC_ID = "facility_id";
    final String DMA_FACTOR = "dma_rank";
    final String COIN = "coin";
    final String VALUE_PER_POP = "value_per_pop";
    final String NOISE = "noise";
    private final RandomGenerator random;

    private Map<IStationInfo, MaxCFStickValues.IValueGenerator> stationToGenerator;

    public Map<IStationInfo, MaxCFStickValues.IValueGenerator> get() {
        return stationToGenerator;
    }

    Map<IStationInfo, Double> stationToCoin;

    public boolean coinFlip(IStationInfo s) {
        return BooleanUtils.toBoolean(new BinomialDistribution(random, 1, stationToCoin.get(s)).sample());
    }

    public PopValueModel(RandomGenerator random, IStationDB stationDB, String csvFile) {
        this.random = random;
        this.stationToCoin = new HashMap<>();
        stationToGenerator = new HashMap<>();
        final Iterable<CSVRecord> records = SimulatorUtils.readCSV(csvFile);
        for (CSVRecord record : records) {
            final int id = Integer.parseInt(record.get(FAC_ID));
            final double dma_factor = Double.parseDouble(record.get(DMA_FACTOR));
            final double coin = Double.parseDouble(record.get(COIN));
            final double valuePerPop = Double.parseDouble(record.get(VALUE_PER_POP));
            final double noise = Double.parseDouble(record.get(NOISE));

            final IStationInfo station = stationDB.getStationById(id);
            stationToCoin.put(station, coin);
            if (station != null) {
                // It is possible to be null here when Ulrich provides a value for a station that was not offered an opening price and is therefore not eligible
                final MaxCFStickValues.IValueGenerator valueGenerator = () -> {
                    return station.getPopulation() * valuePerPop * dma_factor * new LogNormalDistribution(random, 0, noise).sample();
                };
                stationToGenerator.put(station, valueGenerator);
            }
        }
    }

}