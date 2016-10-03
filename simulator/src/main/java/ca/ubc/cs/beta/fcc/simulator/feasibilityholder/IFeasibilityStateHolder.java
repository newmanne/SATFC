package ca.ubc.cs.beta.fcc.simulator.feasibilityholder;

import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader.SATFCProblem;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
import com.google.common.collect.ImmutableSet;

import java.util.Set;

import static ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader.*;

/**
 * Created by newmanne on 2016-08-04.
 */
public interface IFeasibilityStateHolder {

    SATFCProblemSpecification makeProblem(IStationInfo station, Band band);

    default SATFCProblemSpecification makeProblem(Set<IStationInfo> stations, Band band) {
        return makeProblem(stations, ImmutableSet.of(band));
    }

    SATFCProblemSpecification makeProblem(Set<IStationInfo> stations, Set<Band> bands);

}
