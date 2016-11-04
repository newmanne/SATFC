package ca.ubc.cs.beta.fcc.simulator.ladder;

import ca.ubc.cs.beta.fcc.simulator.prevassign.IPreviousAssignmentHandler;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.fcc.simulator.utils.BandHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.Set;

import static ca.ubc.cs.beta.stationpacking.utils.GuavaCollectors.toImmutableList;

/**
 * Created by newmanne on 2016-07-26.
 */
public interface ILadder extends IPreviousAssignmentHandler {

    /**
     * @return the current set of stations in the ladder.
     */
    ImmutableSet<IStationInfo> getStations();

    /**
     * @return the ladder bands, ordered from worst to best (usually OFF -> VHF -> UHF).
     */
    ImmutableList<Band> getBands();

    default ImmutableList<Band> getAirBands() {
        return getBands().stream().filter(BandHelper.AIR_BANDS::contains).collect(toImmutableList());
    }

    /**
     * Get the given station's currently assigned band.
     * @param aStation - the station.
     * @return the band in which the station currently is assigned.
     */
    Band getStationBand(IStationInfo aStation);

    /**
     * Get the stations currently assigned to the given band.
     * @param aBand - the band.
     * @return the set of stations currently assigned to the given band.
     */
    ImmutableSet<IStationInfo> getBandStations(Band aBand);

    /**
     * Get the bands to which the given station could move (i.e. at or above the currently held option)
     * @param aStation - a station.
     * @return set of bands to which the station could move.
     */
    ImmutableList<Band> getPossibleMoves(IStationInfo aStation);

}
