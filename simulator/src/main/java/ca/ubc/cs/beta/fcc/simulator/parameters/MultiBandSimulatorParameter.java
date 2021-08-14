package ca.ubc.cs.beta.fcc.simulator.parameters;

import ca.ubc.cs.beta.fcc.simulator.feasibilityholder.IProblemMaker;
import ca.ubc.cs.beta.fcc.simulator.prevassign.IPreviousAssignmentHandler;
import ca.ubc.cs.beta.fcc.simulator.prices.IPricesFactory;
import ca.ubc.cs.beta.fcc.simulator.solver.IFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.state.RoundTracker;
import ca.ubc.cs.beta.fcc.simulator.unconstrained.ISimulatorUnconstrainedChecker;
import ca.ubc.cs.beta.fcc.simulator.vacancy.IVacancyCalculator;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import com.google.common.eventbus.EventBus;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Created by newmanne on 2016-10-02.
 */
@Data
@Builder
public class MultiBandSimulatorParameter {

    private LadderAuctionParameters parameters;
    private IProblemMaker problemMaker;
    private IFeasibilitySolver solver;
    private IPricesFactory pricesFactory;
    private IVacancyCalculator vacancyCalculator;
    private ISimulatorUnconstrainedChecker unconstrainedChecker;
    private IStationManager stationManager;
    private IConstraintManager constraintManager;
    private RoundTracker roundTracker;
    private SimulatorParameters.BidProcessingAlgorithmParameters bidProcessingAlgorithmParameters;
    private List<Long> forwardAuctionAmounts;
    private boolean isEarlyStopping;
    private EventBus eventBus;
    private boolean lockVHFUntilBase;

}
