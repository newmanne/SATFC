package ca.ubc.cs.beta.fcc.simulator.station;

import com.google.common.base.Preconditions;
import lombok.Data;

import java.util.Set;

/**
 * Created by newmanne on 2016-05-20.
 */ // Class to store attributes of a station that do not change during the course of the simulation
// TODO: hash / equals by id onlys
@Data
public class StationInfo {

    private final int id;
    private final int volume;
    private final Double value;
    private final Nationality nationality;
    private final int homeChannel;
    private Set<Integer> domain;

    public double getValue() {
        Preconditions.checkState(nationality.equals(Nationality.US), "Only US stations have values");
        return value;
    }

}
