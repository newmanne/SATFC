package ca.ubc.cs.beta.fcc.scripts;

import ca.ubc.cs.beta.fcc.simulator.parameters.SimulatorParameters;
import ca.ubc.cs.beta.fcc.simulator.station.CSVStationDB;
import ca.ubc.cs.beta.fcc.simulator.station.IStationDB;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.SimulatorUtils;
import ca.ubc.cs.beta.fcc.vcg.VCGMip;
import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.data.DataManager;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.strategies.AddNeighbourLayerStrategy;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.ConstraintGrouper;
import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.stream.Collectors;

import static ca.ubc.cs.beta.fcc.simulator.parameters.SimulatorParameters.getInternalFile;

public class Script {
    private static Logger log;

    public static void main(String[] args) throws FileNotFoundException {
        log = LoggerFactory.getLogger(Script.class);

        final DataManager dataManager = new DataManager();

        try {
            dataManager.addData(getStationInfoFolder());
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        final IStationDB stationDB = new CSVStationDB(getInternalFile("station_info.csv"), getStationManager(dataManager));

        final String dma = "Pittsburgh, PA";
        int links = 1;
        // We want stations in Pittsburgh DMA
        List<IStationInfo> stationsToKeep = stationDB.getStations().stream().filter(s -> s.getDMA() != null).filter(s -> s.getDMA().equals(dma)).collect(Collectors.toList());

        // Now how would you do links?
        IConstraintManager constraintManager = dataManager.getData(getStationInfoFolder()).getConstraintManager();

        // TODO: THINK ABOUT CLEARING TARGET
        // Step 1: Construct the interference graph based on stations in the DB and their domains
        final Map<Station, Set<Integer>> domains = stationDB.getStations()
                .stream()
                .collect(Collectors.toMap(IStationInfo::toSATFCStation, IStationInfo::getDomain));

        final SimpleGraph<Station, DefaultEdge> constraintGraph = ConstraintGrouper.getConstraintGraph(domains, constraintManager);

        log.info("Found {} stations in DMA {}", stationsToKeep.size(), dma);

        final Iterator<Set<Station>> stationsToPack = new AddNeighbourLayerStrategy().getStationsToPack(constraintGraph, stationsToKeep.stream().map(IStationInfo::toSATFCStation).collect(Collectors.toSet())).iterator();
        final Set<Station> output = stationsToKeep.stream().map(IStationInfo::toSATFCStation).collect(Collectors.toSet());
        for (int i = 0; i < links; i++) {
            if (stationsToPack.hasNext()) {
                output.addAll(stationsToPack.next());
            } else {
                log.info("Exhausted all stations");
            }
        }

        List<List<Object>> records = new ArrayList<>();
        for (IStationInfo s: stationsToKeep) {
            List a = new ArrayList();
            a.add(s.getId());
            records.add(a);
        }
        log.info("Found {} stations total", records.size());


        SimulatorUtils.toCSV("Pittsburgh.csv", Lists.newArrayList("FacID"), records);
    }

    public static IStationManager getStationManager(DataManager dataManager) {
        try {
            return dataManager.getData(getStationInfoFolder()).getStationManager();
        } catch (FileNotFoundException e) {
            throw new IllegalStateException();
        }
    }

    public static String getStationInfoFolder() {
        return getInternalFile("interference_data") + File.separator + "nov2015";
    }


}
