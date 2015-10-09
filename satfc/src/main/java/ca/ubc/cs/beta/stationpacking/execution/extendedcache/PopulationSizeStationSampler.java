package ca.ubc.cs.beta.stationpacking.execution.extendedcache;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math.util.FastMath;

/**
 * Created by emily404 on 6/4/15.
 */
@Slf4j
public class PopulationSizeStationSampler implements IStationSampler {

    private WeightedCollection<Integer> populationMap;

    /**
     * @param populationFile
     * @param keep The set of stations we actually want to sample from
     * @throws IOException
     */
    public PopulationSizeStationSampler(String populationFile, Set<Integer> keep, IStationManager stationManager, int seed) throws IOException {
        populationMap = new WeightedCollection<>(new Random(seed));
        // load population file, put into data structure
        final Path path = FileSystems.getDefault().getPath(populationFile);
        Files.lines(path)
                .skip(1) // skip csv header line
                .forEach(line -> {
                    final String[] split = line.split(",");
                    final Integer station = Integer.parseInt(split[0]);
                    // Make sure we actually know about this station existing
                    try {
                        stationManager.getStationfromID(station);
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("Station information file " + populationFile + " contains an entry for station " + station + " that isn't present in the domain file!");
                    }
                    if (keep.contains(station)) {
                        final Double density = FastMath.sqrt(Double.parseDouble(split[1]));
                        populationMap.add(density, station);
                    }
                });
    }

    @Override
    public Integer sample(Set<Integer> stationsAlreadyInProblem) {
        Integer station = populationMap.next();
        log.debug("Sampling station...");

        while(stationsAlreadyInProblem.contains(station)){
            station = populationMap.next();
        }

        log.debug("Sampled stationID: {}", station);
        return station;
    }

}
