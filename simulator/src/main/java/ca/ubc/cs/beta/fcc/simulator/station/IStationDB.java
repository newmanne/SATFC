package ca.ubc.cs.beta.fcc.simulator.station;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by newmanne on 2016-05-20.
 */
public interface IStationDB {

    IStationInfo getStationById(int stationID);

    Collection<IStationInfo> getStations();

    default Collection<IStationInfo> getStations(Nationality nationality) {
        return getStations().stream().filter(s -> s.getNationality().equals(nationality)).collect(Collectors.toSet());
    }

    interface IModifiableStationDB extends IStationDB {

        void removeStation(int stationID);

    }

}

