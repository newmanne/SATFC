package ca.ubc.cs.beta.stationpacking.execution.extendedcache;


/**
 * Created by newmanne on 15/10/15.
 * A database object providing additional information on stations
 */
public interface IStationDB {

    /**
     * @param id station id
     * @return station population
     */
    int getPopulation(int id);

    /**
     * @param id station id
     * @return station volume
     */
    double getVolume(int id);

}
