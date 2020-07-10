package ca.ubc.cs.beta.stationpacking.execution.problemgenerators.problemparsers;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.execution.Converter;
import ca.ubc.cs.beta.stationpacking.execution.SATFCFacadeExecutor;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.data.DataManager;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.data.ManagerBundle;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;
import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import lombok.Builder;
import lombok.Cleanup;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by newmanne on 2016-11-16.
 */
@Slf4j
public class DatabaseProblemParser extends ANameToProblem {

    private final DataManager dataManager;
    private final Connection connection;
//
//    @Builder
//    @Data
//    public static class SimulatorRedisProblem {
//        private String interference;
//        private String name;
//        private Map<Integer, Integer> previousAssignment;
//        private int maxChannel;
//        private List<Integer> stations;
//    }

    public DatabaseProblemParser(final DataManager dataManager, final String interferencesFolder, final Connection connection) {
        super(interferencesFolder);
        log.info("Reading problems from database");
        this.dataManager = dataManager;
        this.connection = connection;
    }

    public Map<Integer, Set<Integer>> domainsFromStations(String interference, Collection<Integer> stations, int highest) {
        try {
            final ManagerBundle data = dataManager.getData(interference);
            final IStationManager stationManager = data.getStationManager();
            return stations.stream().collect(Collectors.toMap(
                    s -> s,
                    s -> stationManager.getRestrictedDomain(new Station(s), s >= SATFCFacadeExecutor.MIN_CANADIAN_ID ? (highest == 38 ? 36 : highest - 1) : highest, true)
            ));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Converter.StationPackingProblemSpecs getSpecs(String name) throws IOException {
        final List<String> splits = Splitter.on("-").splitToList(name);
        final int sourceId = Integer.parseInt(splits.get(0));
        final int id = Integer.parseInt(splits.get(1));
        // TODO: may be a good idea to use a LEFT OUTER JOIN if you want to allow previous assignment to be null (and then special case that as empty map)
        final String query =
            "SELECT `Problems`.`stations`, `Problems`.`name`, `Problems`.`max_channel`, `Interferences`.`interference`, `Assignments`.`assignment`, `Bands`.`band`\n" +
                    "FROM `Problems`\n" +
                    "INNER JOIN `Assignments` ON `Problems`.`assignment_id` = `Assignments`.`id` AND `Problems`.`source_id` = `Assignments`.`source_id`\n" +
                    "INNER JOIN `Interferences` ON `Problems`.`interference_id` = `Interferences`.`id`\n" +
                    "INNER JOIN `Bands` ON `Problems`.`band_id` = `Bands`.`id`\n" +
                    "WHERE `Problems`.`id` = " + id + " AND `Problems`.`source_id` = " + sourceId + " ;";
        try {
            @Cleanup
            final Statement statement = connection.createStatement();
            @Cleanup
            final ResultSet rs = statement.executeQuery(query);
            final boolean next = rs.next();
            Preconditions.checkState(next, "No rows found?");
            final List<Integer> stations = JSONUtils.getMapper().readValue(rs.getString("stations"), new TypeReference<List<Integer>>() {});
            final Map<Integer, Integer> assignment = JSONUtils.getMapper().readValue(rs.getString("assignment"), new TypeReference<Map<Integer, Integer>>() {
            });
            final int maxChan = rs.getInt("max_channel");
//                final String problemName = rs.getString(4);
            final String interference = rs.getString("interference");
            final String band = rs.getString("band");

            final String intDir = interferencesFolder + File.separator + interference;
            Preconditions.checkState(band.equals("UHF"), "TODO: Need to code VHF");
            final Map<Integer, Set<Integer>> domains = domainsFromStations(intDir, stations, maxChan);
            // Filter previous assignment
            assignment.entrySet().removeIf(e -> !domains.containsKey(e.getKey()) || !domains.get(e.getKey()).contains(e.getValue()));

            return new Converter.StationPackingProblemSpecs(domains, assignment, interference);
        } catch (SQLException e) {
            throw new RuntimeException("Couldn't execute sql statement", e);
        }
    }

    @Override
    protected String getName(String name) {
        return name;
    }

}
