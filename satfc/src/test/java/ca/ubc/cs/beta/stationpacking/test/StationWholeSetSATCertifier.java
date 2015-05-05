package ca.ubc.cs.beta.stationpacking.test;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.IStationSubsetCertifier;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;

import java.util.HashMap;
import java.util.Set;

/**
 * Returns SAT when all the stations in the instance of a station packing problem are contained in the "toPackStations"
 *  parameter (i.e. when the group of neighbors around a starting point has extended to every station in the instance).
 * @author pcernek
 */
public class StationWholeSetSATCertifier implements IStationSubsetCertifier {

    private int numberOfTimesCalled;

    public StationWholeSetSATCertifier() {
        this.numberOfTimesCalled = 0;
    }

    @Override
    public SolverResult certify(StationPackingInstance aInstance, Set<Station> aToPackStations, ITerminationCriterion aTerminationCriterion, long aSeed) {
        numberOfTimesCalled ++;
        if (aToPackStations.containsAll(aInstance.getStations()))
            return new SolverResult(SATResult.SAT, 0, new HashMap<>());
        // Otherwise we return TIMEOUT rather than UNSAT in keeping with the behavior of StationSubsetSATCertifier
        return new SolverResult(SATResult.TIMEOUT, 0);
    }

    @Override
    public void interrupt() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void notifyShutdown() {
        throw new UnsupportedOperationException();
    }

    public int getNumberOfTimesCalled() {
        return numberOfTimesCalled;
    }
}
