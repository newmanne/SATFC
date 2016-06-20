package ca.ubc.cs.beta.fcc.simulator.station;

import lombok.Data;

/**
 * Created by newmanne on 2016-05-20.
 */ // Class to store attributes of a station that do not change during the course of the simulation
// TODO: hash / equals by id onlys
@Data
public class StationInfo implements IStationInfo {

    private final int id;
    private final Integer volume;
    private final Double value;
    private final Nationality nationality;

    public static StationInfo canadianStation(int id) {
        return new StationInfo(id, null, null, Nationality.CA);
    }

}
