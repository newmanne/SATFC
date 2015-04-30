package ca.ubc.cs.beta.stationpacking.test;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.IStationSubsetCertifier;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;

import java.util.Set;

/**
 * Created by pcernek on 4/29/15.
 */
public class AlwaysSATCertifier implements IStationSubsetCertifier {
    @Override
    public SolverResult certify(StationPackingInstance aInstance, Set<Station> aToPackStations, ITerminationCriterion aTerminationCriterion, long aSeed) {
        return null;
    }

    @Override
    public void interrupt() throws UnsupportedOperationException {

    }

    @Override
    public void notifyShutdown() {

    }
}
