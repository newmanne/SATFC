package ca.ubc.cs.beta.fcc.simulator.feasibilityholder;

import ca.ubc.cs.beta.fcc.simulator.solver.problem.ProblemType;
import ca.ubc.cs.beta.fcc.simulator.solver.problem.SimulatorProblem;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;

import java.util.Set;

/**
 * Created by newmanne on 2016-08-04.
 */
public interface IProblemMaker {

    /**
     * Create a problem that adds station to band along with all other stations on the ladder in that band
     */
    SimulatorProblem makeProblem(IStationInfo station, Band band, ProblemType problemType, String name);

    default SimulatorProblem makeProblem(IStationInfo station, Band band, ProblemType problemType) {
        return makeProblem(station, band, problemType, null);
    }

    /**
     * Create a problem testing a set of stations on a given band
     */
    SimulatorProblem makeProblem(Set<IStationInfo> stations, Band band, ProblemType problemType, IStationInfo targetStation, String name);

    default SimulatorProblem makeProblem(Set<IStationInfo> stations, Band band, ProblemType problemType, IStationInfo targetStation) {
        return makeProblem(stations, band, problemType, targetStation,  null);
    }

}
