package ca.ubc.cs.beta.stationpacking.execution.extendedcache;

import java.io.IOException;
import java.util.Random;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

/**
 * Created by emily404 on 6/4/15.
 */
@Slf4j
public class PopulationVolumeSampler implements IStationSampler {

    private WeightedCollection<Integer> weightedStationMap;
    /**
     * @param keep The set of stations we actually want to sample from
     * @throws IOException
     */
    public PopulationVolumeSampler(IStationDB stationDB, Set<Integer> keep, int seed)  {
        weightedStationMap = new WeightedCollection<>(new Random(seed));
        for (Integer stationID : keep) {
            double weight = ((double) stationDB.getPopulation(stationID)) / stationDB.getVolume(stationID);
            weightedStationMap.add(weight, stationID);
        }
    }

    @Override
    public Integer sample(Set<Integer> stationsAlreadyInProblem) {
        Integer station = weightedStationMap.next();
        log.debug("Sampling station...");

        while(stationsAlreadyInProblem.contains(station)){
            station = weightedStationMap.next();
        }

        log.debug("Sampled stationID: {}", station);
        return station;
    }

}
