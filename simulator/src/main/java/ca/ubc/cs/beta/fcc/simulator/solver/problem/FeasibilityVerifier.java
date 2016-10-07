//package ca.ubc.cs.beta.fcc.simulator.solver.problem;
//
///**
// * Created by newmanne on 2016-08-04.
// */
//
//import ca.ubc.cs.beta.fcc.simulator.solver.IFeasibilityVerifier;
//import ca.ubc.cs.beta.stationpacking.base.Station;
//import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.ChannelSpecificConstraintManager;
//import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
//import ca.ubc.cs.beta.stationpacking.datamanagers.stations.DomainStationManager;
//import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
//import ca.ubc.cs.beta.stationpacking.facade.datamanager.data.DataManager;
//import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;
//import lombok.NonNull;
//import lombok.extern.slf4j.Slf4j;
//
//import java.io.FileNotFoundException;
//import java.util.Map;
//
///**
// * Uses a constraintManager to check if the current station to channel mapping
// * is feasible
// *
// * @author alimv
// */
//@Slf4j
//public class FeasibilityVerifier implements IFeasibilityVerifier {
//
//    private final IConstraintManager constraintManager;
//    private final IStationManager stationManager;
//
//    public FeasibilityVerifier(String configFolder) {
//        try {
//            stationManager = new DomainStationManager(configFolder + DataManager.DOMAIN_FILE);
//            constraintManager = new ChannelSpecificConstraintManager(stationManager, configFolder + DataManager.INTERFERENCES_FILE);
//        } catch (FileNotFoundException e) {
//            throw new IllegalArgumentException("Could not create feasibility verifier from interference config folder " + configFolder + " (" + e.getMessage() + ").");
//        }
//
//    }
//
//    public FeasibilityVerifier(@NonNull IConstraintManager constraintManager, @NonNull IStationManager stationManager) {
//        this.constraintManager = constraintManager;
//        this.stationManager = stationManager;
//    }
//
//    /**
//     * Checks the current mapping from stations to channels against domain and
//     * interference constraints
//     *
//     * @param stationToChannel a mapping from station to channel
//     * @return true if and only if the input mapping is feasible
//     */
//    public boolean isFeasibleAssignment (Map<Station, Integer> stationToChannel) {
//        return constraintManager.isSatisfyingAssignment(StationPackingUtils.channelToStationFromStationToChannel(stationToChannel));
//    }
//
//}