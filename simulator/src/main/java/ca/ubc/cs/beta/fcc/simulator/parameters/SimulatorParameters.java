package ca.ubc.cs.beta.fcc.simulator.parameters;

import ca.ubc.cs.beta.aeatk.misc.options.UsageTextField;
import ca.ubc.cs.beta.aeatk.options.AbstractOptions;
import ca.ubc.cs.beta.fcc.simulator.Simulator;
import ca.ubc.cs.beta.fcc.simulator.participation.IParticipationDecider;
import ca.ubc.cs.beta.fcc.simulator.participation.OneHundredPerCentParticipation;
import ca.ubc.cs.beta.fcc.simulator.participation.OpeningPriceHigherThanPrivateValue;
import ca.ubc.cs.beta.fcc.simulator.solver.DistributedFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.IFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.LocalFeasibilitySolver;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.execution.parameters.SATFCFacadeParameters;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.data.DataManager;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParametersDelegate;
import lombok.Getter;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Created by newmanne on 2016-05-20.
 */
@UsageTextField(title = "Simulator Parameters", description = "Simulator Parameters")
public class SimulatorParameters extends AbstractOptions {

    @Getter
    @Parameter(names = "-INFO-FILE", description = "csv file")
    private String infoFile;

    @Getter
    @Parameter(names = "-SEND-QUEUE", description = "queue name to send work on")
    private String sendQueue;
    @Getter
    @Parameter(names = "-LISTEN-QUEUE", description = "queue name to listen for work on")
    private String listenQueue;

    @Getter
    @Parameter(names = "-BASE-CLOCK")
    private double baseClockPrice = 900;

    @Getter
    @Parameter(names = "-MAX-CHANNEL", description = "highest available channel")
    private int maxChannel = 29;


    @Getter
    @Parameter(names = "-CONSTRAINT-SET", description = "constraint set name (not full path!)")
    private String constraintSet = "032416SC46U";

    public String getStationInfoFolder() {
        return facadeParameters.fInterferencesFolder + File.separator + constraintSet;
    }

    public double getCutoff() {
        return facadeParameters.fInstanceParameters.Cutoff;
    }

    public void setUp() {
        final File outputFolder = new File(getOutputFolder());
        if (outputFolder.exists()) {
            outputFolder.delete();
        }
        outputFolder.mkdirs();
        new File(getStateFolder()).mkdir();

        dataManager = new DataManager();
        try {
            dataManager.addData(getStationInfoFolder());
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        problemGenerator = new Simulator.ProblemGeneratorImpl(getMaxChannel(), getStationManager());
    }

    private String getStateFolder() {
        return getOutputFolder() + File.separator + "state";
    }



    @Getter
    @ParametersDelegate
    private SATFCFacadeParameters facadeParameters = new SATFCFacadeParameters();

    public long getSeed() {
        return facadeParameters.fInstanceParameters.Seed;
    }

    @Getter
    @Parameter(names = "-SIMULATOR-OUTPUT-FOLDER", description = "output file name")
    private String outputFolder = "output";

    @Parameter(names = "-SOLVER-TYPE", description = "Type of solver")
    private SolverType solverType = SolverType.LOCAL;

    @Parameter(names = "-PARTICIPATION-MODEL", description = "Type of solver")
    private ParticipationModel participationModel = ParticipationModel.PRICE_HIGHER_THAN_VALUE;

    public Simulator.IStateSaver getStateSaver() {
        return new Simulator.SaveStateToFile(getStateFolder());
    }

    private DataManager dataManager;

    public IStationManager getStationManager() {
        try {
            return dataManager.getData(getStationInfoFolder()).getStationManager();
        } catch (FileNotFoundException e) {
            throw new IllegalStateException();
        }
    }

    public IConstraintManager getConstraintManager() {
        try {
            return dataManager.getData(getStationInfoFolder()).getConstraintManager();
        } catch (FileNotFoundException e) {
            throw new IllegalStateException();
        }
    }

    @Getter
    private Simulator.IProblemGenerator problemGenerator;

    public IFeasibilitySolver createSolver() {
        final Simulator.IProblemGenerator problemGenerator = new Simulator.ProblemGeneratorImpl(getMaxChannel(), getStationManager());
        final Simulator.ISATFCProblemSpecGenerator problemSpecGenerator = new Simulator.ISATFCProblemSpecGeneratorImpl(problemGenerator, getStationInfoFolder(), getCutoff(), getSeed());
        switch (solverType) {
            case LOCAL:
                return new LocalFeasibilitySolver(problemSpecGenerator);
            case DISTRIBUTED:
                return new DistributedFeasibilitySolver(problemSpecGenerator, facadeParameters.fRedisParameters.getJedis(), sendQueue, listenQueue);
            default:
                throw new IllegalStateException();
        }

    }

    public enum ParticipationModel {
        ALL,
        PRICE_HIGHER_THAN_VALUE
    }

    public IParticipationDecider getParticipationDecider(Simulator.Prices prices) {
        switch (participationModel) {
            case ALL:
                return new OneHundredPerCentParticipation();
            case PRICE_HIGHER_THAN_VALUE:
                return new OpeningPriceHigherThanPrivateValue(prices);
            default:
                throw new IllegalStateException();
        }
    }
}
