//package ca.ubc.cs.beta.fcc.simulator.feasibilityholder;
//
///**
// * Created by newmanne on 2016-08-04.
// */
//
//import ca.ubc.cs.beta.fcc.simulator.ladder.ILadder;
//import ca.ubc.cs.beta.fcc.simulator.ladder.ILadderEventOnMoveListener;
//import ca.ubc.cs.beta.fcc.simulator.prevassign.IPreviousAssignmentHandler;
//import ca.ubc.cs.beta.fcc.simulator.solver.IFeasibilitySolver;
//import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
//import ca.ubc.cs.beta.fcc.simulator.utils.Band;
//import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
//import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
//import com.google.common.base.Preconditions;
//import lombok.NonNull;
//import org.jgrapht.graph.DefaultEdge;
//import org.jgrapht.graph.SimpleGraph;
//
//import java.util.Collection;
//import java.util.Map;
//
///**
// * @author afrechet
// */
//
//public class LazyFeasibilityStateHolder implements IFeasibilityStateHolder, ILadderEventOnMoveListener {
//
//    private final ILadder ladder;
//    //    private final Map<Band, SimpleGraph<IStationInfo, DefaultEdge>> fConstraintGraphs;
//    private final IFeasibilitySolver solver;
//    private final IPreviousAssignmentHandler previousAssignmentHandler;
//    private final FeasibilityCache feasibilityCache;
//
//    public LazyFeasibilityStateHolder(
//            @NonNull ILadder ladder,
//            @NonNull Map<Band, SimpleGraph<IStationInfo, DefaultEdge>> aConstraintGraphs,
//            @NonNull IFeasibilitySolver solver,
//            @NonNull IPreviousAssignmentHandler previousAssignmentHandler
//    ) {
//        this.ladder = ladder;
//        this.solver = solver;
//        this.previousAssignmentHandler = previousAssignmentHandler;
//        feasibilityCache = new FeasibilityCache(ladder.getStations(), ladder.getAirBands());
//    }
//
//    @Override
//    public void onMove(IStationInfo station, Band prevBand, Band newBand, ILadder ladder) {
//
//        if (!prevBand.equals(Band.OFF)) {
//            //Every station that is connected to the moved station in the source band that is infeasible must be dirtied.
//            SimpleGraph<IStation, DefaultEdge> sourceConstraintGraph = fConstraintGraphs.get(aPrevBand);
//            ConnectivityInspector<IStation, DefaultEdge> sourceConnectivityInspector = new ConnectivityInspector<IStation, DefaultEdge>(sourceConstraintGraph);
//            Set<IStation> sourceComponent = sourceConnectivityInspector.connectedSetOf(aMovedStation);
//            for (IStation station : sourceComponent) {
//                if (!feasibilityCache.isDirty(station, aPrevBand) && !feasibilityCache.getFeasibility(station, aPrevBand).isFeasible()) {
//                    feasibilityCache.setDirty(station, aPrevBand, true);
//                }
//            }
//        }
//
//        if (!aChosenBand.equals(Band.OFF)) {
//            //Every station that is connected to the moved station in the target band that is feasible must be dirtied.
//            SimpleGraph<IStation, DefaultEdge> targetConstraintGraph = fConstraintGraphs.get(aChosenBand);
//            ConnectivityInspector<IStation, DefaultEdge> targetConnectivityInspector = new ConnectivityInspector<IStation, DefaultEdge>(targetConstraintGraph);
//            Set<IStation> targetComponent = targetConnectivityInspector.connectedSetOf(aMovedStation);
//            for (IStation station : targetComponent) {
//                if (!feasibilityCache.isDirty(station, aChosenBand) && feasibilityCache.getFeasibility(station, aChosenBand).isFeasible()) {
//                    feasibilityCache.setDirty(station, aChosenBand, true);
//                }
//            }
//        }
//
//    }
//
//    @Override
//    public FeasibilityCheckerResult getFeasibility(IStation aStation, Band band) {
//        Preconditions.checkState();
//
//        clean(aStation, aBand);
//
//        FeasibilityCheckerResult feasibility = feasibilityCache.getFeasibility(aStation, aBand);
//
//        if (feasibility == null) {
//            throw new IllegalStateException("Feasibility was not previously computed for station " + aStation + " on band " + aBand + ".");
//        }
//
//        return feasibility;
//
//    }
////
////    /**
////     * Clean the given station on the given band, potentially submitting the necessary packing problem if needed.
////     *
////     * @param aStation
////     * @param aBand
////     */
////    private void clean(IStation aStation, Band aBand) {
////        boolean dirty = feasibilityCache.isDirty(aStation, aBand);
////
////        if (dirty) {
////            Set<IStation> bandStations = ladder.getBandStations(aBand);
////
////            Set<IStation> stationsToPack = new HashSet<IStation>();
////            stationsToPack.addAll(bandStations);
////            stationsToPack.add(aStation);
////            stationsToPack.removeAll(fDRP.getImpairedStations());
////
////            //Submit corresponding feasibility checker problem.
////            final FeasibilityCheckerProblem problem = FeasibilityCheckerProblem.constructSingleBandPackingProblem(stationsToPack,
////                    aBand,
////                    previousAssignmentHandler);
////            final BlockingFeasibilityCheckerCallback callback = new BlockingFeasibilityCheckerCallback();
////            solver.checkFeasibility(problem, callback);
////
////            final FeasibilityCheckerResult result = callback.getResult();
////
////            //Update station in question.
////            feasibilityCache.setFeasibility(aStation, aBand, result);
////            feasibilityCache.setDirty(aStation, aBand, false);
////
////            if (result.isFeasible()) {
////                previousAssignmentHandler.updatePreviousAssignment(result.getWitnessAssignment());
////            }
////
////            //Update all stations on this band as well if (1) result was feasible, (2) station was already on this band.
////            if (result.isFeasible() || ladder.getStationBand(aStation).equals(aBand)) {
////                Map<Integer, Integer> restrictedAssignment = new HashMap<Integer, Integer>(result.getWitnessAssignment());
////                if (!ladder.getStationBand(aStation).equals(aBand)) {
////                    restrictedAssignment.remove(aStation.getID());
////                }
////
////                FeasibilityCheckerResult restrictedResult = new FeasibilityCheckerResult(result.getResult(), restrictedAssignment);
////                for (IStation station : bandStations) {
////                    feasibilityCache.setFeasibility(station, aBand, restrictedResult);
////                    feasibilityCache.setDirty(station, aBand, false);
////                }
////            }
////        }
////
////    }
//
////    private static class FeasibilityCache {
////        private final Map<IStation, Map<Band, Boolean>> fDirtyBits;
////        private final Map<IStation, Map<Band, FeasibilityCheckerResult>> fFeasibility;
////
////        public FeasibilityCache(Collection<IStation> aStations, Collection<Band> aBands) {
////            //TODO Initialize cache as clean instead of dirty with a feasible assignment.
////
////            fDirtyBits = new ConcurrentHashMap<IStation, Map<Band, Boolean>>();
////            fFeasibility = new ConcurrentHashMap<IStation, Map<Band, FeasibilityCheckerResult>>();
////
////            for (IStation station : aStations) {
////                Map<Band, Boolean> stationDirtyBits = new ConcurrentHashMap<Band, Boolean>();
////                Map<Band, FeasibilityCheckerResult> stationFeasibility = new ConcurrentHashMap<Band, FeasibilityCheckerResult>();
////
////                for (Band band : aBands) {
////                    stationDirtyBits.put(band, true);
////                    stationFeasibility.put(band, new FeasibilityCheckerResult(null, Maps.newHashMap()));
////                }
////
////                fDirtyBits.put(station, stationDirtyBits);
////                fFeasibility.put(station, stationFeasibility);
////            }
////        }
////
////        public synchronized void setDirty(IStation aStation, Band aBand, boolean aDirty) {
////            if (!fDirtyBits.containsKey(aStation)) {
////                throw new IllegalStateException("No dirty bits for station " + aStation + ".");
////            }
////            final Map<Band, Boolean> stationDirtyBits = fDirtyBits.get(aStation);
////            if (!stationDirtyBits.containsKey(aBand)) {
////                throw new IllegalStateException("No dirty bits for station " + aStation + " on band " + aBand + ".");
////            }
////            stationDirtyBits.put(aBand, aDirty);
////        }
////
////        public synchronized boolean isDirty(IStation aStation, Band aBand) {
////            if (!fDirtyBits.containsKey(aStation)) {
////                throw new IllegalStateException("No dirty bits for station " + aStation + ".");
////            }
////            final Map<Band, Boolean> stationDirtyBits = fDirtyBits.get(aStation);
////            if (!stationDirtyBits.containsKey(aBand)) {
////                throw new IllegalStateException("No dirty bits for station " + aStation + " on band " + aBand + ".");
////            }
////            return stationDirtyBits.get(aBand);
////        }
////
////        public synchronized void setFeasibility(IStation aStation, Band aBand, FeasibilityCheckerResult aFeasibility) {
////            Preconditions.checkState(fFeasibility.containsKey(aStation), "No feasibility results for station " + aStation + ".");
////            Map<Band, FeasibilityCheckerResult> stationFeasibility = fFeasibility.get(aStation);
////            Preconditions.checkState(stationFeasibility.containsKey(aBand), "No feasibiltiy result for station " + aStation + " on band " + aBand + ".");
////            stationFeasibility.put(aBand, aFeasibility);
////        }
////
////        public synchronized FeasibilityCheckerResult getFeasibility(IStation aStation, Band aBand) {
////            Preconditions.checkState(fFeasibility.containsKey(aStation), "No feasibility results for station " + aStation + ".");
////            Map<Band, FeasibilityCheckerResult> stationFeasibility = fFeasibility.get(aStation);
////            Preconditions.checkState(stationFeasibility.containsKey(aBand), "No feasibiltiy result for station " + aStation + " on band " + aBand + ".");
////            return fFeasibility.get(aStation).get(aBand);
////        }
////
////    }
//
//}