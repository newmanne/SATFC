/**
 * Copyright 2016, Auctionomics, Alexandre Fréchette, Neil Newman, Kevin Leyton-Brown.
 * <p>
 * This file is part of SATFC.
 * <p>
 * SATFC is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * SATFC is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with SATFC.  If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * For questions, contact us at:
 * afrechet@cs.ubc.ca
 */
package ca.ubc.cs.beta.stationpacking.execution;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.execution.extendedcache.CSVStationDB;
import ca.ubc.cs.beta.stationpacking.execution.extendedcache.IStationDB;
import ca.ubc.cs.beta.stationpacking.facade.InterruptibleSATFCResult;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.data.DataManager;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.data.ManagerBundle;
import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;
import com.google.common.base.Preconditions;
import lombok.Cleanup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.ParameterException;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;

import ca.ubc.cs.beta.aeatk.misc.jcommander.JCommanderHelper;
import ca.ubc.cs.beta.aeatk.misc.returnvalues.AEATKReturnValues;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.init.TargetAlgorithmEvaluatorLoader;
import ca.ubc.cs.beta.stationpacking.execution.metricwriters.IMetricWriter;
import ca.ubc.cs.beta.stationpacking.execution.metricwriters.MetricWriterFactory;
import ca.ubc.cs.beta.stationpacking.execution.parameters.SATFCFacadeParameters;
import ca.ubc.cs.beta.stationpacking.execution.problemgenerators.CutoffChooserFactory;
import ca.ubc.cs.beta.stationpacking.execution.problemgenerators.ICutoffChooser;
import ca.ubc.cs.beta.stationpacking.execution.problemgenerators.IProblemReader;
import ca.ubc.cs.beta.stationpacking.execution.problemgenerators.ProblemGeneratorFactory;
import ca.ubc.cs.beta.stationpacking.execution.problemgenerators.SATFCFacadeProblem;
import ca.ubc.cs.beta.stationpacking.facade.SATFCFacade;
import ca.ubc.cs.beta.stationpacking.facade.SATFCFacadeBuilder;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
import ca.ubc.cs.beta.stationpacking.metrics.SATFCMetrics;

/**
 * Executes a SATFC facade built from parameters on an instance given in parameters.
 *
 * @author afrechet
 */
public class SATFCFacadeExecutor {

    public static final int MIN_CANADIAN_ID = 1000001;

    /**
     * @param args - parameters satisfying {@link SATFCFacadeParameters}.
     */
    public static void main(String[] args) throws Exception {
        //Parse the command line arguments in a parameter object.
        @Cleanup
        SATFCFacadeParameters parameters = new SATFCFacadeParameters();
        Logger log = parseParameter(args, parameters);
        logVersionInfo(log);
        try {
            log.info("Initializing facade.");
            try (final SATFCFacade satfc = SATFCFacadeBuilder.builderFromParameters(parameters).build()) {
                if (parameters.augment) {
                    augment(parameters, log, satfc);
                } else {
                    solveProblems(parameters, log, satfc);
                }
            }
        } catch (ParameterException e) {
            log.error("Invalid parameter argument detected.", e);
            e.printStackTrace();
            System.exit(AEATKReturnValues.PARAMETER_EXCEPTION);
        } catch (RuntimeException e) {
            log.error("Runtime exception encountered ", e);
            e.printStackTrace();
            System.exit(AEATKReturnValues.UNCAUGHT_EXCEPTION);
        } catch (UnsatisfiedLinkError e) {
            log.error("Couldn't initialize facade, see previous log messages and/or try logging with DEBUG.", e);
            System.exit(AEATKReturnValues.UNCAUGHT_EXCEPTION);
        } catch (Throwable t) {
            log.error("Throwable encountered ", t);
            t.printStackTrace();
            System.exit(AEATKReturnValues.UNCAUGHT_EXCEPTION);
        }
        log.info("Normal termination. Goodbye");
    }

    private static void augment(SATFCFacadeParameters parameters, Logger log, SATFCFacade satfc) throws FileNotFoundException {
        Preconditions.checkArgument(parameters.cachingParams.serverURL != null, "No server URL specified!");
        final DataManager dataManager = new DataManager();
        final ManagerBundle managerBundle = dataManager.getData(parameters.augmentConstraintSet);
        final IStationManager stationManager = managerBundle.getStationManager();
        final Map<Integer, Set<Integer>> domains = new HashMap<>();
        stationManager.getStations().stream().forEach(s -> {
            // Canadian stations should be capped 1 channel lower than US channels
            int maxChan = s.getID() >= MIN_CANADIAN_ID ? parameters.augmentChannel - 1 : parameters.augmentChannel;
            final Set<Integer> domain = stationManager.getRestrictedDomain(s, maxChan, true);
            if (!domain.isEmpty()) {
                domains.put(s.getID(), domain);
            }
        });
        log.info("Found {} stations with UHF domains and channels in the range {}-{}", domains.size(), StationPackingUtils.UHFmin, parameters.augmentChannel);
        final Map<Integer, Integer> startingAssignment = parameters.fInstanceParameters.getPreviousAssignment();
        log.info("Starting from assignment {}", startingAssignment);
        final IStationDB stationDB = new CSVStationDB(parameters.augmentConstraintSet + File.separator + "info.csv");
        satfc.augment(domains, startingAssignment, stationDB, parameters.augmentConstraintSet, parameters.fInstanceParameters.Cutoff, parameters.minimumAugmentStations);
    }

    private static void solveProblems(SATFCFacadeParameters parameters, Logger log, SATFCFacade satfc) {

        final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

        IProblemReader problemReader = ProblemGeneratorFactory.createFromParameters(parameters);


        ICutoffChooser cutoffChooser = CutoffChooserFactory.createFromParameters(parameters);
        IMetricWriter metricWriter = MetricWriterFactory.createFromParameters(parameters);
        SATFCFacadeProblem problem;
        while ((problem = problemReader.getNextProblem()) != null) {
            final double cutoff = cutoffChooser.getCutoff(problem);
            log.info("Beginning problem {} with cutoff {}", problem.getInstanceName(), cutoff);
            log.info("Solving ...");
            final InterruptibleSATFCResult interruptibleSATFCResult = satfc.solveInterruptibly(
                    problem.getDomains(),
                    problem.getPreviousAssignment(),
                    cutoff,
                    parameters.fInstanceParameters.Seed,
                    problem.getStationConfigFolder(),
                    problem.getInstanceName()
            );
            // Submit a job to check Redis every X time and call interrupt
            ScheduledFuture<?> scheduledFuture = null;
            if (problemReader instanceof SimulatorProblemReader) {
                scheduledFuture = executorService.scheduleWithFixedDelay(() -> {
                    if (((SimulatorProblemReader) problemReader).shouldInterrupt()) {
                        interruptibleSATFCResult.interrupt();
                    }
                }, 0, 100, TimeUnit.MILLISECONDS);
            }
            final SATFCResult result = interruptibleSATFCResult.computeResult();
            if (scheduledFuture != null) {
                scheduledFuture.cancel(true);
            }
            log.info("..done!");
            if (!log.isInfoEnabled()) {
                System.out.println(result.getResult());
                System.out.println(result.getRuntime());
                System.out.println(result.getWitnessAssignment());
            } else {
                log.info("Result:" + System.lineSeparator() + result.getResult() + System.lineSeparator() + result.getRuntime() + System.lineSeparator() + result.getWitnessAssignment());
            }
            problemReader.onPostProblem(problem, result);
            metricWriter.writeMetrics();
            SATFCMetrics.clear();
        }

        log.info("Finished all of the problems!");
        problemReader.onFinishedAllProblems();
        metricWriter.onFinished();
    }

    private static Logger parseParameter(String[] args, SATFCFacadeParameters parameters) {
        Logger log;
        try {
            //Check for help
            JCommanderHelper.parseCheckingForHelpAndVersion(args, parameters, TargetAlgorithmEvaluatorLoader.getAvailableTargetAlgorithmEvaluators());
            SATFCFacadeBuilder.initializeLogging(parameters.getLogLevel(), parameters.logFileName);
            JCommanderHelper.logCallString(args, SATFCFacadeExecutor.class);
        } finally {
            log = LoggerFactory.getLogger(SATFCFacadeExecutor.class);
        }
        return log;
    }

    public static void logVersionInfo(Logger log) {
        try {
            final String versionProperties = Resources.toString(Resources.getResource("version.properties"), Charsets.UTF_8);
            log.info("Version info: " + System.lineSeparator() + versionProperties);
        } catch (IllegalArgumentException | IOException e) {
            log.error("Could not log version info.");
        }
    }

}