//package ca.ubc.cs.beta.fcc.simulator.ladder;
//
///**
// * Created by newmanne on 2016-07-26.
// */
//
//import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
//import ca.ubc.cs.beta.fcc.simulator.utils.Band;
//
//import java.util.HashSet;
//import java.util.List;
//import java.util.Set;
//
//import static ca.ubc.cs.beta.stationpacking.utils.GuavaCollectors.toImmutableSet;
//
///**
// * Implements the concept of a band ladder on which stations are allowed
// * to move freely below their home bands until they come back to
// * their home/initial band.
// *
// * @author afrechet
// */
//public class UnidirectionalLadder extends ASimpleLadder {
//
//    public UnidirectionalLadder(List<Band> aLadderBands) {
//        super(aLadderBands);
//    }
//
//    public UnidirectionalLadder(UnidirectionalLadder aLadder) {
//        super(aLadder);
//    }
//
//    @Override
//    protected boolean isValidMove(IStationInfo station, Band band) {
//        return (band.isAboveOrEqualTo(getStationBand(station))) && (getRung(band) <= getRung(station.getHomeBand()));
//    }
//
//    @Override
//    public List<Band> getPossibleMoves(IStationInfo aStation) {
//
//        //Get initial rung.
//        int homeRung = getRung(aStation.getHomeBand());
//
//        //All the possible moves are the bands below the home rung.
//        Set<Band> possibleMoves = new HashSet<>();
//        for (int r = getRung(aStation); r <= homeRung; r++) {
//            possibleMoves.add(getBandAtRung(r));
//        }
//
//        return possibleMoves;
//    }
//
//    @Override
//    public Set<Band> getValidMoves(IStationInfo aStation) {
//        return getPossibleMoves(aStation).stream().filter(band -> isValidMove(aStation, band)).collect(toImmutableSet());
//    }
//}
