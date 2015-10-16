package ca.ubc.cs.beta.stationpacking.execution.extendedcache;

import au.com.bytecode.opencsv.CSVReader;
import ca.ubc.cs.beta.stationpacking.base.Station;
import com.google.common.base.Preconditions;
import lombok.Data;

import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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

    public static class CSVStationDB implements IStationDB {

        final Map<Integer, Integer> stationToPopulation;
        final Map<Integer, Double> stationToVolume;

        /**
         * @param stationFile a path to a csv file with a header and columns "id, population, volume"
         */
        public CSVStationDB(String stationFile) {
            // TODO: check about skipping header
            // TODO: what if no volume?
            stationToPopulation = new HashMap<>();
            stationToVolume = new HashMap<>();
            try (CSVReader reader = new CSVReader(new FileReader(stationFile))) {
                String[] line;
                while ((line = reader.readNext()) != null) {
                    final int id = Integer.parseInt(line[0].trim());
                    final int population = Integer.parseInt(line[1].trim());
                    final double volume = Double.parseDouble(line[2].trim());
                    Preconditions.checkState(!stationToVolume.containsKey(id) && !stationToVolume.containsKey(id), "Previous value associated with station id %d", id);
                    stationToPopulation.put(id, population);
                    stationToVolume.put(id, volume);
                }
            } catch (IOException e) {
                throw new IllegalArgumentException("Could not read station information file: " + stationFile, e);
            }
        }

        @Override
        public int getPopulation(int id) {
            Preconditions.checkState(stationToPopulation.containsKey(id), "No known population for station with id %d", id);
            return stationToPopulation.get(id);
        }

        @Override
        public double getVolume(int id) {
            Preconditions.checkState(stationToPopulation.containsKey(id), "No known population for station with id %d", id);
            return stationToVolume.get(id);
        }
    }

}
