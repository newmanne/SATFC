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
package ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import ca.ubc.cs.beta.stationpacking.solvers.termination.composite.DisjunctiveCompositeTerminationCriterion;
import ca.ubc.cs.beta.stationpacking.solvers.termination.cputime.CPUTimeTerminationCriterion;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Sets;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.alg.NeighborIndex;
import org.jgrapht.graph.DefaultEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.metrics.SATFCMetrics;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.SolverData;
import ca.ubc.cs.beta.stationpacking.solvers.SolverHelper;
import ca.ubc.cs.beta.stationpacking.solvers.base.Assignment;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.ConstraintGrouper;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.utils.Watch;

/**
 * Pre-solve by applying a sequence of station subsets certifiers based on
 * the neighborhood of stations missing from a problem instance's previous assignment.
 * <p/>
 * If an UNSAT result is found for the immediate local neighborhood of the missing stations, the search
 * is expanded to include the neighbors' neighbors. This is iterated until any of the following conditions is met:
 * <ol>
 * <li>A SAT result is found</li>
 * <li>The expanded neighborhood contains all the stations connected (directly or indirectly) to the original
 * missing stations</li>
 * <li>The search times out</li>
 * </ol>
 *
 * @author afrechet
 * @author pcernek
 */
@Slf4j
public class ConstraintGraphNeighborhoodPresolver implements ISolver {

    public interface IStationAddingStrategy {

        Iterable<StationPackingConfiguration> getConfigurations(ITerminationCriterion terminationCriterion, StationPackingInstance stationPackingInstance);

    }

    @Value
    public static class StationPackingConfiguration {
        private final double cutoff;
        private final Set<Station> packingStations;
    }

    @RequiredArgsConstructor
    public static class AddNeighbourLayerStrategy implements IStationAddingStrategy {

        private final int maxLayers;

        @Override
        public Iterable<StationPackingConfiguration> getConfigurations(ITerminationCriterion criterion, StationPackingInstance stationPackingInstance, NeighborIndex<Station, DefaultEdge> neighbourIndex, Set<Station> missingStations) {
            final AtomicReference<StationPackingConfiguration> currentReference = new AtomicReference<>();
            final AtomicInteger neighbourLayer = new AtomicInteger();
            return () -> new AbstractIterator<StationPackingConfiguration>() {

                int numIters = 0;

                @Override
                protected StationPackingConfiguration computeNext() {
                    if (numIters > maxLayers) {
                        return endOfData();
                    }
                    final StationPackingConfiguration prev = currentReference.get();
                    final Set<Station> newToPack;
                    if (prev == null) {
                        // First round! Just grab the neighbours
                        newToPack = missingStations.stream().map(neighbourIndex::neighborsOf).flatMap(Collection::stream).collect(Collectors.toSet());
                    } else {
                        final Set<Station> packingStations = prev.getPackingStations();
                        newToPack = packingStations.stream().map(neighbourIndex::neighborsOf).flatMap(Collection::stream).collect(Collectors.toSet());
                        if (!packingStations.addAll(newToPack)) {
                            return endOfData();
                        }
                    }
                    numIters++;
                    final StationPackingConfiguration newConfiguration = new StationPackingConfiguration(criterion.getRemainingTime(), newToPack);
                    currentReference.set(newConfiguration);
                    return newConfiguration;
                }

            };
        }
    }

    public static final int UNLIMITED_NEIGHBOR_LAYERS = -1;

    private final IConstraintManager fConstraintManager;
    private final List<IStationSubsetCertifier> fCertifiers;
    private final IStationAddingStrategy fStationAddingStrategy;

    /**
     * <p>
     * Produces a presolver, whose default behavior is to exhaustively search neighbors of neighbors until there
     * are no more neighbors left to add (unless a SAT solution is found first).
     * </p>
     * <p>
     * The equivalent of calling the three-parameter constructor, with
     * {@link ConstraintGraphNeighborhoodPresolver#UNLIMITED_NEIGHBOR_LAYERS} as the third argument.
     * </p>
     *
     * @param aConstraintManager - indicates the interference constraints between stations.
     * @param aCertifiers        - the list of certifiers to use to evaluate the satisfiability of station subsets.
     */
//    public ConstraintGraphNeighborhoodPresolver(IConstraintManager aConstraintManager, List<IStationSubsetCertifier> aCertifiers) {
//        this(aConstraintManager, aCertifiers, UNLIMITED_NEIGHBOR_LAYERS);
//    }

    /**
     * @param aConstraintManager   - indicates the interference constraints between stations.
     * @param aCertifiers          - the list of certifiers to use to evaluate the satisfiability of station subsets.
     * @param maxLayersOfNeighbors - a positive number specifying the maximum number of layers of neighbors that the
     *                             p			watch.stop();
     *                             resolver should explore. The value
     *                             {@link ConstraintGraphNeighborhoodPresolver#UNLIMITED_NEIGHBOR_LAYERS}
     *                             indicates that the presolver should
     *                             exhaustively search neighbors of neighbors until there are no more neighbors left
     *                             to add (unless a SAT solution is found first).
     */
    public ConstraintGraphNeighborhoodPresolver(IConstraintManager aConstraintManager, List<IStationSubsetCertifier> aCertifiers, IStationAddingStrategy aStationAddingStrategy) {
        this.fConstraintManager = aConstraintManager;
        this.fCertifiers = aCertifiers;
        this.fStationAddingStrategy = aStationAddingStrategy;
    }

    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
        final Watch watch = Watch.constructAutoStartWatch();
        final Set<Station> stationsWithNoPreviousAssignment = getStationsNotInPreviousAssignment(aInstance);
        log.debug("There are {} stations that are not part of previous assignment.", stationsWithNoPreviousAssignment.size());

        // Check if there are too many stations to make this procedure worthwhile.
        if (stationsWithNoPreviousAssignment.size() == aInstance.getDomains().size()) {
            log.debug("Too many missing stations in previous assignment ({}).", stationsWithNoPreviousAssignment.size());
            return SolverResult.createTimeoutResult(watch.getElapsedTime());
        }

        if (aTerminationCriterion.hasToStop()) {
            log.debug("All time spent.");
            return SolverResult.createTimeoutResult(watch.getElapsedTime());
        }

        final NeighborIndex<Station, DefaultEdge> constraintGraphNeighborIndex = buildNeighborIndex(aInstance);

        if (aTerminationCriterion.hasToStop()) {
            log.debug("All time spent.");
            return SolverResult.createTimeoutResult(watch.getElapsedTime());
        }

        final List<SolverResult> results = new ArrayList<>();
        for (final StationPackingConfiguration configuration : fStationAddingStrategy.getConfigurations(aTerminationCriterion, aInstance)) {
            for (int i = 0; i < fCertifiers.size() && !aTerminationCriterion.hasToStop(); i++) {
                log.debug("Trying constraint graph neighborhood certifier {}.", i + 1);

                IStationSubsetCertifier certifier = fCertifiers.get(i);
                final ITerminationCriterion criterion = new DisjunctiveCompositeTerminationCriterion(Arrays.asList(aTerminationCriterion, new CPUTimeTerminationCriterion(configuration.getCutoff())));

                watch.stop();
                final SolverResult result = certifier.certify(aInstance, configuration.getPackingStations(), criterion, aSeed);
                watch.start();

                results.add(result);

                if (result.getResult().isConclusive()) {
                    SATFCMetrics.postEvent(new SATFCMetrics.SolvedByEvent(aInstance.getName(), SATFCMetrics.SolvedByEvent.PRESOLVER, result.getResult()));
                    return result.getResult().equals(SATResult.SAT);
                }
            }
        }

        logResult(combinedResult);

        return combinedResult;
    }

    private void logResult(SolverResult combinedResult) {
        log.debug("Result:");
        log.debug(combinedResult.toParsableString());
    }

    private NeighborIndex<Station, DefaultEdge> buildNeighborIndex(StationPackingInstance aInstance) {
        log.debug("Building constraint graph.");
        return new NeighborIndex<>(ConstraintGrouper.getConstraintGraph(aInstance.getDomains(), fConstraintManager));
    }

    private Set<Station> getStationsNotInPreviousAssignment(StationPackingInstance aInstance) {
        return aInstance.getStations().stream().filter(station -> !aInstance.getPreviousAssignment().containsKey(station)).collect(Collectors.toSet());
    }

    @Override
    public void notifyShutdown() {
        fCertifiers.forEach(IStationSubsetCertifier::notifyShutdown);
    }

    @Override
    public void interrupt() {
        fCertifiers.forEach(IStationSubsetCertifier::interrupt);
    }
}
