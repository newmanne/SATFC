/**
 * Copyright 2015, Auctionomics, Alexandre Fréchette, Neil Newman, Kevin Leyton-Brown.
 *
 * This file is part of SATFC.
 *
 * SATFC is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SATFC is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SATFC.  If not, see <http://www.gnu.org/licenses/>.
 *
 * For questions, contact us at:
 * afrechet@cs.ubc.ca
 */
package ca.ubc.cs.beta.stationpacking.facade;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import ca.ubc.cs.beta.stationpacking.execution.extendedcache.IStationSampler;
import ca.ubc.cs.beta.stationpacking.execution.extendedcache.PopulationSizeStationSampler;
import com.google.common.base.Predicate;
import com.google.common.collect.Maps;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.execution.parameters.solver.sat.ClaspLibSATSolverParameters;
import ca.ubc.cs.beta.stationpacking.execution.parameters.solver.sat.UBCSATLibSATSolverParameters;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.data.DataManager;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.SolverManager;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.ISolverBundle;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.SATFCHydraBundle;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.YAMLBundle;
import ca.ubc.cs.beta.stationpacking.metrics.SATFCMetrics;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.nonincremental.Clasp3SATSolver;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.nonincremental.ubcsat.UBCSATSolver;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.solvers.termination.walltime.WalltimeTerminationCriterion;
import ca.ubc.cs.beta.stationpacking.utils.TimeLimitedCodeBlock;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

/**
 * A facade for solving station packing problems with SATFC.
 * Each instance of the facade corresponds to an independent copy
 * of SATFC (with different state).
 *
 * @author afrechet
 */
@Slf4j
public class SATFCFacade implements AutoCloseable {

    private final SolverManager fSolverManager;

    /**
     * Construct a SATFC solver facade
     *
     * @param aSATFCParameters parameters needed by the facade.
     */
    SATFCFacade(final SATFCFacadeParameter aSATFCParameters) {
        //Check provided library.
        validateLibraries(aSATFCParameters.getClaspLibrary(), aSATFCParameters.getUbcsatLibrary());

        log.info("Using clasp library {}", aSATFCParameters.getClaspLibrary());
        log.info("Using ubcsat library {}", aSATFCParameters.getUbcsatLibrary());
        log.info("Using bundle {}", aSATFCParameters.getSolverChoice());
        
        fSolverManager = new SolverManager(
                dataBundle -> {
                    switch (aSATFCParameters.getSolverChoice()) {
                        case HYDRA:
                            return new SATFCHydraBundle(dataBundle, aSATFCParameters.getHydraParams(), aSATFCParameters.getClaspLibrary(), aSATFCParameters.getUbcsatLibrary());
                        case YAML:
                            return new YAMLBundle(dataBundle, aSATFCParameters.getConfigFile(), aSATFCParameters.getServerURL(), aSATFCParameters.getClaspLibrary(), aSATFCParameters.getUbcsatLibrary(), aSATFCParameters.getResultFile(), aSATFCParameters.getCNFSaver());
                        default:
                            throw new IllegalArgumentException("Unrecognized solver choice " + aSATFCParameters.getSolverChoice());
                    }
                },
                aSATFCParameters.getDataManager() == null ? new DataManager() : aSATFCParameters.getDataManager()
        );
    }

	private void validateLibraries(final String claspLib, final String ubcsatLib) {
		validateLibraryFile(claspLib);
        validateLibraryFile(ubcsatLib);
        try {
            new Clasp3SATSolver(claspLib, ClaspLibSATSolverParameters.UHF_CONFIG_04_15_h1);
        } catch (UnsatisfiedLinkError e) {
            unsatisfiedLinkWarn(claspLib, e);
        }
        try {
        	new UBCSATSolver(ubcsatLib,  UBCSATLibSATSolverParameters.DEFAULT_DCCA);
        }catch (UnsatisfiedLinkError e) {
            unsatisfiedLinkWarn(ubcsatLib, e);
        }
	}

	private void unsatisfiedLinkWarn(final String libPath, UnsatisfiedLinkError e) {
		log.error("\n--------------------------------------------------------\n" +
		                "Could not load native library : {}. \n" +
		                "Possible Solutions:\n" +
		                "1) Try rebuilding the library, on Linux this can be done by going to the clasp folder and running \"bash compile.sh\"\n" +
		                "2) Check that all library dependancies are met, e.g., run \"ldd {}\".\n" +
		                "3) Manually set the library to use with the \"-CLASP-LIBRARY\" or \"-UBCSAT-LIBRARY\" options.\n" +
		                "--------------------------------------------------------", libPath,libPath);
		throw new IllegalArgumentException("Could not load JNA library", e);
	}

	private void validateLibraryFile(final String libraryFilePath) {
		Preconditions.checkNotNull(libraryFilePath, "Cannot provide null library");
        final File libraryFile = new File(libraryFilePath);
        Preconditions.checkArgument(libraryFile.exists(), "Provided library " + libraryFile.getAbsolutePath() + " does not exist.");
        Preconditions.checkArgument(!libraryFile.isDirectory(), "Provided library is a directory.");
	}

    /**
     * Solve a station packing problem.
     *
     * @param aDomains             - a map taking integer station IDs to set of integer channels domains.
     * @param aPreviousAssignment  - a valid (proved to not create any interference) partial (can concern only some of the provided station) station to channel assignment.
     * @param aCutoff              - a cutoff in seconds for SATFC's execution.
     * @param aSeed                - a long seed for randomization in SATFC.
     * @param aStationConfigFolder - a folder in which to find station config data (<i>i.e.</i> interferences and domains files).
     * @return a result about the packability of the provided problem, with the time it took to solve, and corresponding valid witness assignment of station IDs to channels.
     */
    public SATFCResult solve(
            @NonNull Map<Integer, Set<Integer>> aDomains,
            @NonNull Map<Integer, Integer> aPreviousAssignment,
            double aCutoff,
            long aSeed,
            @NonNull String aStationConfigFolder,
            String instanceName) {
        if (aDomains.isEmpty()) {
            log.warn("Provided an empty domains map.");
            return new SATFCResult(SATResult.SAT, 0.0, ImmutableMap.of());
        }
        Preconditions.checkArgument(aCutoff > 0, "Cutoff must be strictly positive");

        final ISolverBundle bundle = getSolverBundle(aStationConfigFolder);

        final IStationManager stationManager = bundle.getStationManager();

        log.debug("Translating arguments to SATFC objects...");
        //Translate arguments.
        final Map<Station, Set<Integer>> domains = new HashMap<>();

        for (Entry<Integer, Set<Integer>> entry : aDomains.entrySet()) {
            final Station station = stationManager.getStationfromID(entry.getKey());

            final Set<Integer> domain = entry.getValue();
            final Set<Integer> completeStationDomain = stationManager.getDomain(station);

            final Set<Integer> trueDomain = Sets.intersection(domain, completeStationDomain);

            if (trueDomain.isEmpty()) {
                log.warn("Station {} has an empty domain, cannot pack.", station);
                return new SATFCResult(SATResult.UNSAT, 0.0, ImmutableMap.of());
            }

            domains.put(station, trueDomain);
        }

        final Map<Station, Integer> previousAssignment = new HashMap<>();
        for (Station station : domains.keySet()) {
            final Integer previousChannel = aPreviousAssignment.get(station.getID());
            if (previousChannel != null && previousChannel > 0) {
                Preconditions.checkState(domains.get(station).contains(previousChannel), "Provided previous assignment assigned channel " + previousChannel + " to station "+station+" which is not in its problem domain "+ domains.get(station)+".");
                previousAssignment.put(station, previousChannel);
            }
        }

        log.debug("Constructing station packing instance...");
        //Construct the instance.
        final Map<String, Object> metadata = new HashMap<>();
        if (instanceName != null) {
            metadata.put(StationPackingInstance.NAME_KEY, instanceName);
        }
        StationPackingInstance instance = new StationPackingInstance(domains, previousAssignment, metadata);
        SATFCMetrics.postEvent(new SATFCMetrics.NewStationPackingInstanceEvent(instance, bundle.getConstraintManager()));

        log.debug("Getting solver...");
        //Get solver
        final ISolver solver = bundle.getSolver(instance);
		
		/*
		 * Logging problem info
		 */
        log.debug("Solving instance {} ...", instance);
        log.debug("Instance stats:");
        log.debug("{} stations.", instance.getStations().size());
        log.debug("stations: {}.", instance.getStations());
        log.debug("{} all channels.", instance.getAllChannels().size());
        log.debug("all channels: {}.", instance.getAllChannels());
        log.debug("Previous assignment: {}", instance.getPreviousAssignment());

        log.debug("Setting termination criterion...");
        //Set termination criterion.
        final ITerminationCriterion termination = new WalltimeTerminationCriterion(aCutoff);

        // Make sure that SATFC doesn't get hung. We give a VERY generous timeout window before throwing an exception
        final int SUICIDE_GRACE_IN_SECONDS = 5 * 60;
        final long totalTimeInMillis = (long) (aCutoff + SUICIDE_GRACE_IN_SECONDS) * 1000;

        //Solve instance.
        SolverResult result = null;
        try {
            result = TimeLimitedCodeBlock.runWithTimeout(() -> solver.solve(instance, termination, aSeed), totalTimeInMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new RuntimeException("SATFC waited " + totalTimeInMillis + " ms for a result, but no result came back! The given timeout was " + aCutoff + " s, so SATFC appears to be hung. This is probably NOT a recoverable error");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        SATFCMetrics.postEvent(new SATFCMetrics.InstanceSolvedEvent(instanceName, result));

        log.debug("Transforming result into SATFC output...");
        //Transform back solver result to output result.
        final Map<Integer, Integer> witness = new HashMap<>();
        for (Entry<Integer, Set<Station>> entry : result.getAssignment().entrySet()) {
            Integer channel = entry.getKey();
            for (Station station : entry.getValue()) {
                witness.put(station.getID(), channel);
            }
        }

        final SATFCResult outputResult = new SATFCResult(result.getResult(), result.getRuntime(), witness);

        log.debug("Result: {}.", outputResult);

        return outputResult;

    }

    private ISolverBundle getSolverBundle(String aStationConfigFolder) {
        log.debug("Getting data managers...");
        //Get the data managers and solvers corresponding to the provided station config data.
        final ISolverBundle bundle;
        try {
            bundle = fSolverManager.getData(aStationConfigFolder);
        } catch (FileNotFoundException e) {
            log.error("Did not find the necessary data files in provided station config data folder {}.", aStationConfigFolder);
            throw new IllegalArgumentException("Station config files not found.", e);
        }
        return bundle;
    }

    public SATFCResult solve(Set<Integer> aStations,
                             Set<Integer> aChannels,
                             Map<Integer, Set<Integer>> aReducedDomains,
                             Map<Integer, Integer> aPreviousAssignment,
                             double aCutoff,
                             long aSeed,
                             String aStationConfigFolder
    ) {
        return solve(aStations, aChannels, aReducedDomains, aPreviousAssignment, aCutoff, aSeed, aStationConfigFolder, null);
    }

    public SATFCResult solve(
            Map<Integer, Set<Integer>> aDomains,
            Map<Integer, Integer> aPreviousAssignment,
            double aCutoff,
            long aSeed,
            String aStationConfigFolder) {
        return solve(aDomains, aPreviousAssignment, aCutoff, aSeed, aStationConfigFolder, null);
    }


    /**
     * Solve a station packing problem. The channel domain of a station will be the intersection of the station's original domain (given in data files) with the packing channels,
     * and additionally intersected with its reduced domain if available and if non-empty.
     *
     * @param aStations            - a collection of integer station IDs.
     * @param aChannels            - a collection of integer channels.
     * @param aReducedDomains      - a map taking integer station IDs to set of integer channels domains.
     * @param aPreviousAssignment  - a valid (proved to not create any interference) partial (can concern only some of the provided station) station to channel assignment.
     * @param aCutoff              - a cutoff in seconds for SATFC's execution.
     * @param aSeed                - a long seed for randomization in SATFC.
     * @param aStationConfigFolder - a folder in which to find station config data (<i>i.e.</i> interferences and domains files).
     * @return a result about the packability of the provided problem, with the time it took to solve, and corresponding valid witness assignment of station IDs to channels.
     */
    public SATFCResult solve(@NonNull Set<Integer> aStations,
                             @NonNull Set<Integer> aChannels,
                             @NonNull Map<Integer, Set<Integer>> aReducedDomains,
                             @NonNull Map<Integer, Integer> aPreviousAssignment,
                             double aCutoff,
                             long aSeed,
                             String aStationConfigFolder,
                             String instanceName
    ) {
        log.debug("Transforming instance to a domains only instance.");

        //Construct the domains map.
        final Map<Integer, Set<Integer>> aDomains = new HashMap<>();
        for (Integer station : aStations) {
            Set<Integer> domain = new HashSet<>(aChannels);
            Set<Integer> reducedDomain = aReducedDomains.get(station);
            if (reducedDomain != null && !reducedDomain.isEmpty()) {
                domain = Sets.intersection(domain, reducedDomain);
            }
            aDomains.put(station, domain);
        }
        return solve(aDomains, aPreviousAssignment, aCutoff, aSeed, aStationConfigFolder, instanceName);
    }

    public void augment(@NonNull Map<Integer, Set<Integer>> domains,
                        @NonNull Map<Integer, Integer> previousAssignment,
                        @NonNull String stationInformationFile,
                        @NonNull String stationConfigFolder,
                        int seed,
                        double cutoff
    ) {
        // These stations will be in every single problem
        final Set<Integer> exitedStations = previousAssignment.keySet();

        final ISolverBundle bundle = getSolverBundle(stationConfigFolder);
        final IStationManager manager = bundle.getStationManager();

        // Init the station sampler
        final IStationSampler sampler;
        try {
            sampler = new PopulationSizeStationSampler(stationInformationFile, domains.keySet(), manager, seed);
        } catch (IOException e) {
            throw new IllegalArgumentException("Problem parsing file " + stationInformationFile, e);
        }

        // The set of stations that we are going to pack in every problem
        final Set<Integer> packingStations = new HashSet<>(exitedStations);
        final Set<Integer> confirmedUNSAT = new HashSet<>();
        Map<Integer, Integer> currentAssignment = new HashMap<>(previousAssignment);

        // TODO: global restart if you fail

        // Augment
        while (true) {
            // Sample a new station
            final Integer sample = sampler.sample(Sets.union(packingStations, confirmedUNSAT));
            final Set<Integer> toPack = Sets.union(packingStations, Collections.singleton(sample));
            final Map<Integer, Set<Integer>> reducedDomains = Maps.filterEntries(domains, new Predicate<Entry<Integer, Set<Integer>>>() {
                @Override
                public boolean apply(Entry<Integer, Set<Integer>> input) {
                    return toPack.contains(input.getKey());
                }
            });

            // Solve!
            final SATFCResult result = solve(reducedDomains, currentAssignment, cutoff, seed, stationConfigFolder);
            if (result.getResult().equals(SATResult.SAT)) {
                // Result was SAT. Let's continue down this trajectory
                log.debug("Result was SAT. Adding station {} to trajectory", sample);
                packingStations.add(sample);
                currentAssignment = result.getWitnessAssignment();
            } else {
                log.debug("Result was not {}, not SAT", result.getResult());
                if (result.getResult().equals(SATResult.UNSAT)) {
                    log.debug("Station {} is confirmed UNSAT, will not try it again", sample);
                    confirmedUNSAT.add(sample);
                }
            }
        }
    }

    public void augment(@NonNull Map<Integer, Set<Integer>> domains,
                        @NonNull Map<Integer, Integer> previousAssignment,
                        @NonNull String stationInformationFile,
                        @NonNull String stationConfigFolder,
                        double cutoff) {
        augment(domains, previousAssignment, stationInformationFile, stationConfigFolder, new Random().nextInt(), cutoff);
    }

    @Override
    public void close() throws Exception {
        log.info("Shutting down...");
        fSolverManager.close();
        log.info("Goodbye!");
    }

}
