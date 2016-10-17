package ca.ubc.cs.beta.fcc.simulator.parameters;

import ca.ubc.cs.beta.fcc.simulator.feasibilityholder.IFeasibilityStateHolder;
import ca.ubc.cs.beta.fcc.simulator.prevassign.IPreviousAssignmentHandler;
import ca.ubc.cs.beta.fcc.simulator.prices.IPricesFactory;
import ca.ubc.cs.beta.fcc.simulator.solver.IFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.unconstrained.ISimulatorUnconstrainedChecker;
import ca.ubc.cs.beta.fcc.simulator.vacancy.IVacancyCalculator;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import lombok.Data;
import lombok.experimental.Builder;

/**
 * Created by newmanne on 2016-10-02.
 */
@Data
@Builder
public class MultiBandSimulatorParameter {

    private LadderAuctionParameters parameters;
    private IPreviousAssignmentHandler previousAssignmentHandler;
    private IFeasibilityStateHolder problemMaker;
    private IFeasibilitySolver solver;
    private IPricesFactory pricesFactory;
    private IVacancyCalculator vacancyCalculator;
    private ISimulatorUnconstrainedChecker unconstrainedChecker;
    private IStationManager stationManager;
    private IConstraintManager constraintManager;

}
