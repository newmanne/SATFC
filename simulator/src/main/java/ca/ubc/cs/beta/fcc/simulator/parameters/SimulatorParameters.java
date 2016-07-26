package ca.ubc.cs.beta.fcc.simulator.parameters;

import ca.ubc.cs.beta.aeatk.misc.options.UsageTextField;
import ca.ubc.cs.beta.aeatk.options.AbstractOptions;
import ca.ubc.cs.beta.fcc.simulator.Simulator;
import ca.ubc.cs.beta.fcc.simulator.participation.IParticipationDecider;
import ca.ubc.cs.beta.fcc.simulator.participation.OpeningPriceHigherThanPrivateValue;
import ca.ubc.cs.beta.fcc.simulator.prices.Prices;
import ca.ubc.cs.beta.fcc.simulator.scoring.FCCScoringRule;
import ca.ubc.cs.beta.fcc.simulator.scoring.IScoringRule;
import ca.ubc.cs.beta.fcc.simulator.solver.DistributedFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.IFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.LocalFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.problem.IProblemGenerator;
import ca.ubc.cs.beta.fcc.simulator.solver.problem.ISATFCProblemSpecGeneratorImpl;
import ca.ubc.cs.beta.fcc.simulator.solver.problem.ProblemGeneratorImpl;
import ca.ubc.cs.beta.fcc.simulator.state.IStateSaver;
import ca.ubc.cs.beta.fcc.simulator.state.SaveStateToFile;
import ca.ubc.cs.beta.fcc.simulator.station.*;
import ca.ubc.cs.beta.fcc.simulator.utils.SimulatorUtils;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.execution.parameters.SATFCFacadeParameters;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.data.DataManager;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParametersDelegate;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import lombok.Getter;
import org.apache.commons.csv.CSVRecord;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Created by newmanne on 2016-05-20.
 */
@UsageTextField(title = "Simulator Parameters", description = "Simulator Parameters")
public class SimulatorParameters extends AbstractOptions {

    @Getter
    @Parameter(names = "-INFO-FILE", description = "csv file")
    private String infoFile = "/ubc/cs/research/arrow/satfc/simulator/data/simulator.csv";

    @Getter
    @Parameter(names = "-VOLUMES-FILE", description = "volumes file")
    private String volumeFile = "/ubc/cs/research/arrow/satfc/simulator/data/volumes.csv";

    @Getter
    @Parameter(names = "-SEND-QUEUE", description = "queue name to send work on")
    private String sendQueue = "send";
    @Getter
    @Parameter(names = "-LISTEN-QUEUE", description = "queue name to listen for work on")
    private String listenQueue = "listen";

    @Getter
    @Parameter(names = "-UNIT-VOLUME", description = "Sets all stations to have unit volume")
    private boolean unitVolume = false;

    @Getter
    @Parameter(names = "-BASE-CLOCK")
    private double baseClockPrice = 900;

    @Getter
    @Parameter(names = "-IGNORE-CANADA")
    private boolean ignoreCanada = true;

    @Parameter(names = "-SCORING-RULE")
    private ScoringRule scoringRule = ScoringRule.FCC;

    @Getter
    @Parameter(names = "-MAX-CHANNEL", description = "highest available channel")
    private int maxChannel = 29;

    @Getter
    @Parameter(names = "-CONSTRAINT-SET", description = "constraint set name (not full path!)")
    private String constraintSet = "032416SC46U";

    @Getter
    @Parameter(names = "-RESTORE-SIMULATION", description = "Restore simulation from state folder")
    private boolean restore = false;

    public String getStationInfoFolder() {
        return facadeParameters.fInterferencesFolder + File.separator + constraintSet;
    }

    public double getCutoff() {
        return facadeParameters.fInstanceParameters.Cutoff;
    }

    public void setUp() {
        final File outputFolder = new File(getOutputFolder());
        if (isRestore()) {
            Preconditions.checkState(outputFolder.exists() && outputFolder.isDirectory(), "Expected to restore state but no state directory found!");
        } else {
            if (outputFolder.exists()) {
                outputFolder.delete();
            }
            outputFolder.mkdirs();
            new File(getStateFolder()).mkdir();
        }

        dataManager = new DataManager();
        try {
            dataManager.addData(getStationInfoFolder());
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        problemGenerator = new ProblemGeneratorImpl(getMaxChannel(), getStationManager());
        final Predicate<IStationInfo> ignore = x -> isIgnoreCanada() && x.getNationality().equals(Nationality.CA);
        final Function<IStationInfo, IStationInfo> decorators = x -> {
            IStationInfo decorated = x;
            if (isUnitVolume()) {
                decorated = new UnitVolumeDecorator(decorated);
            }
            return decorated;
        };

        stationDB = new CSVStationDB(getInfoFile(), getVolumeFile(), getStationManager(), ignore, decorators);
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

    public IStateSaver getStateSaver() {
        return new SaveStateToFile(getStateFolder());
    }


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

    private DataManager dataManager;
    @Getter
    private IProblemGenerator problemGenerator;
    @Getter
    private StationDB stationDB;

    public IFeasibilitySolver createSolver() {
        final IProblemGenerator problemGenerator = new ProblemGeneratorImpl(getMaxChannel(), getStationManager());
        final Simulator.ISATFCProblemSpecGenerator problemSpecGenerator = new ISATFCProblemSpecGeneratorImpl(problemGenerator, getStationInfoFolder(), getCutoff(), getSeed());
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
        PRICE_HIGHER_THAN_VALUE
    }

    public IParticipationDecider getParticipationDecider(Prices prices) {
        switch (participationModel) {
            case PRICE_HIGHER_THAN_VALUE:
                return new OpeningPriceHigherThanPrivateValue(prices);
            default:
                throw new IllegalStateException();
        }
    }

    public enum ScoringRule {
        FCC,
    }

    public IScoringRule getScoringRule() {
        switch (scoringRule) {
            case FCC:
                return new FCCScoringRule(getBaseClockPrice());
            default:
                throw new IllegalStateException();
        }
    }

}
