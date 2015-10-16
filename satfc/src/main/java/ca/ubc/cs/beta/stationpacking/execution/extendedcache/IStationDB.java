package ca.ubc.cs.beta.stationpacking.execution.extendedcache;

import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import au.com.bytecode.opencsv.CSVReader;

import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;

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
