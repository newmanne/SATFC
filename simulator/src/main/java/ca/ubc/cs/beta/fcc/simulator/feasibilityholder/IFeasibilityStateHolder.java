package ca.ubc.cs.beta.fcc.simulator.feasibilityholder;

import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import com.google.common.collect.ImmutableSet;

import java.util.Set;

import static ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader.SATFCProblemSpecification;

/**
 * Created by newmanne on 2016-08-04.
 */
public interface IFeasibilityStateHolder {

    SATFCProblemSpecification makeProblem(IStationInfo station, Band band, String name);

    default SATFCProblemSpecification makeProblem(Set<IStationInfo> stations, Band band, String name) {
        return makeProblem(stations, ImmutableSet.of(band), name);
    }

    SATFCProblemSpecification makeProblem(Set<IStationInfo> stations, Set<Band> bands, String name);

    String BID_PROCESSING_HOME_BAND_FEASIBILITY = "BidProcessingHome";
    String BID_PROCESSING_MOVE_FEASIBILITY = "BidProcessingMove";
    String BID_STATUS_UPDATING_HOME_BAND_FEASIBILITY = "BidStatusUpdating";
    String PROVISIONAL_WINNER_CHECK = "ProvisionalWinner";
    String INITIAL_PLACEMENT = "InitialPlacement";

}
