package ca.ubc.cs.beta.fcc.simulator;

import ca.ubc.cs.beta.fcc.simulator.station.CSVStationDB;
import ca.ubc.cs.beta.fcc.simulator.station.StationDB;
import ca.ubc.cs.beta.fcc.simulator.station.StationInfo;
import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.ChannelSpecificConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.Constraint;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.DomainStationManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.data.DataManager;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.data.ManagerBundle;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.strategies.AddNeighbourLayerStrategy;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.ConstraintGrouper;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;
import com.google.common.base.Joiner;
import com.google.common.io.Resources;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by newmanne on 2016-05-26.
 */
public class SimulatorTest {

    @Test
    @Ignore
    public void neighbourHoods() {
        ManagerBundle bundle = bundle();
        final IConstraintManager constraintManager = bundle.getConstraintManager();
        final IStationManager stationManager = bundle.getStationManager();
        StationDB stationDB = new CSVStationDB("/ubc/cs/research/arrow/satfc/simulator/data/simulator.csv");
        final Map<Station, Set<Integer>> domains = stationDB.getStations().stream().map(StationInfo::getId).collect(Collectors.toMap(Station::new, s -> stationManager.getRestrictedDomain(new Station(s), 29, true)));
        //final Map<Station, Set<Integer>> domains = stationManager.getStations().stream().collect(Collectors.toMap(s -> s, s -> stationManager.getRestrictedDomain(s, 29, true)));
        final SimpleGraph<Station, DefaultEdge> constraintGraph = ConstraintGrouper.getConstraintGraph(domains, constraintManager);
        // NY
        final List<Integer> input = Arrays.asList(1328,73881,6048,47535,73356,9610,22206,73207,14322);
        // LA
        // final List<Integer> input = Arrays.asList(282,21422,22208,33742,191101,3167,60026,13058,35670,35123,47906,38430,26231, 9628, 167309);
        final Set<Station> startingPoint = new HashSet<>(input).stream().filter(s -> stationDB.getStationById(s) != null)
                                                               .map(Station::new).collect(Collectors.toSet());
        System.out.println(Joiner.on(',').join(startingPoint));
        final Iterable<Set<Station>> stationsToPack = new AddNeighbourLayerStrategy().getStationsToPack(constraintGraph, startingPoint);
        for (Set<Station> stations : stationsToPack) {
            System.out.println(Joiner.on(',').join(stations));
        }
    }

    @Test
    public void constraints() throws IOException {
        ManagerBundle bundle = bundle();
        final IConstraintManager constraintManager = bundle.getConstraintManager();
        final IStationManager stationManager = bundle.getStationManager();
        final Map<Station, Set<Integer>> domains = stationManager.getStations().stream()
                .collect(Collectors.toMap(s -> s, s -> stationManager.getRestrictedDomain(s, 13, false)));
        Map<Pair<Station, Station>, Integer> weights = new HashMap<>();
        for (Constraint constraint : constraintManager.getAllRelevantConstraints(domains)) {
            Station lower = constraint.getSource().getID() < constraint.getTarget().getID() ? constraint.getSource() : constraint.getTarget();
            Station upper = constraint.getSource().equals(lower) ? constraint.getTarget() : constraint.getSource();
            weights.merge(Pair.of(lower, upper), 1, (k,v) -> v + 1);
        }
        FileUtils.writeStringToFile(new File("/tmp/output.json"), JSONUtils.toString(weights));
    }

    public static ManagerBundle bundle() {
        try {
            final String path = "/Users/newmanne/research/interference-data/032416SC46U";
            DomainStationManager stationManager = new DomainStationManager(path + File.separator + DataManager.DOMAIN_FILE);
            ChannelSpecificConstraintManager constraintManager = new ChannelSpecificConstraintManager(stationManager, path + File.separator + DataManager.INTERFERENCES_FILE);
            return new ManagerBundle(stationManager, constraintManager, path);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

}