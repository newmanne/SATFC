package ca.ubc.cs.beta.stationpacking.execution.extendedcache;

/**
 * Created by emily404 on 6/4/15.
 */
public class StationSamplerFactory{

    public static IStationSampler getStationSampler(StationSamplingMethod sampler){
        switch(sampler){
            case POPULATION_SIZE:
                return new PopulationSizeStationSampler();
        }
        return null;
    }

    public enum StationSamplingMethod {
        POPULATION_SIZE
    }
}
