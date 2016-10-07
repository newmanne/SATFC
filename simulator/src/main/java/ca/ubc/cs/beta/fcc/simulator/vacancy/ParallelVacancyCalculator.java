package ca.ubc.cs.beta.fcc.simulator.vacancy;

import ca.ubc.cs.beta.fcc.simulator.ladder.ILadder;
import ca.ubc.cs.beta.fcc.simulator.participation.Participation;
import ca.ubc.cs.beta.fcc.simulator.participation.ParticipationRecord;
import ca.ubc.cs.beta.fcc.simulator.solver.IFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.IFeasibilityVerifier;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.fcc.simulator.utils.BandHelper;
import ca.ubc.cs.beta.fcc.simulator.utils.GraphUtils;
import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.ConstraintGrouper;
import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.alg.NeighborIndex;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ca.ubc.cs.beta.fcc.simulator.utils.BigDecimalUtils.SUM;
import static ca.ubc.cs.beta.stationpacking.utils.GuavaCollectors.toImmutableSet;

/**
 * Created by newmanne on 2016-07-26.
 */

@Slf4j
public class ParallelVacancyCalculator implements IVacancyCalculator {

    public ParallelVacancyCalculator(ParticipationRecord participation,
                                     IFeasibilityVerifier feasibilityVerifier,
                                     IConstraintManager constraintManager,
                                     double feasibilityVacancyContributionFloor,
                                     int nCores
    ) {
        this.nCores = nCores;
        sequentialVacancyCalculator = new SequentialVacancyCalculator(feasibilityVerifier, participation, constraintManager, feasibilityVacancyContributionFloor);
    }

    private final SequentialVacancyCalculator sequentialVacancyCalculator;
    private final int nCores;

    /**
     * @param ladder                   - the auction's ladder structure.
     * @param assignment - the current feasible channel assignment.
     * @return a map taking each station and band in the ladder to the station's vacancy on the band.
     */
    @Override
    public ImmutableTable<IStationInfo, Band, Double> computeVacancies(
            @NonNull final Collection<IStationInfo> stations,
            @NonNull final ILadder ladder,
            @NonNull final Map<Integer, Integer> assignment
    ) {
        final ImmutableTable<IStationInfo, Band, Set<IStationInfo>> bandNeighborIndexMap = sequentialVacancyCalculator.getBandNeighborIndexMap(ladder);
        final Map<IStationInfo, Map<Band, Double>> vacancies = new ConcurrentHashMap<>(ladder.getAirBands().size());
        final ForkJoinPool forkJoinPool = new ForkJoinPool(nCores);
        try {
            forkJoinPool.submit(() -> {
                        stations.parallelStream().forEach(station -> {
                            Map<Band, Double> stationVacancies = new EnumMap<>(Band.class);
                            for (Band band : ladder.getAirBands()) {
                                stationVacancies.put(band, sequentialVacancyCalculator.computeVacancy(station, band, ladder, assignment, bandNeighborIndexMap));
                            }
                            vacancies.put(station, stationVacancies);
                        });
                    }
            ).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Couldn't compute vacancies in parallel!", e);
        }

        // convert to table for API purposes... sort of silly
        final ImmutableTable.Builder<IStationInfo, Band, Double> builder = ImmutableTable.builder();
        vacancies.entrySet().forEach(outerEntry -> {
            final IStationInfo station = outerEntry.getKey();
            outerEntry.getValue().entrySet().forEach(innerEntry -> {
                builder.put(station, innerEntry.getKey(), innerEntry.getValue());
            });
        });
        return builder.build();
    }

    @RequiredArgsConstructor
    public static class SequentialVacancyCalculator implements IVacancyCalculator {

        @NonNull
        private final IFeasibilityVerifier feasibilityVerifier;
        @NonNull
        private final ParticipationRecord participationRecord;
        @NonNull
        private final IConstraintManager constraintManager;

        private final double VAC_FLOOR;

        @Override
        public ImmutableTable<IStationInfo, Band, Double> computeVacancies(@NonNull Collection<IStationInfo> stations, @NonNull ILadder ladder, @NonNull Map<Integer, Integer> assignment) {
            final ImmutableTable<IStationInfo, Band, Set<IStationInfo>> bandNeighbourhoods = getBandNeighborIndexMap(ladder);
            final ImmutableTable.Builder<IStationInfo, Band, Double> builder = ImmutableTable.builder();
            for (IStationInfo station : stations) {
                for (Band band : ladder.getAirBands()) {
                    final double vacancy = computeVacancy(station, band, ladder, assignment, bandNeighbourhoods);
                    builder.put(station, band, vacancy);
                }
            }
            return builder.build();
        }

        private ImmutableTable<IStationInfo, Band, Set<IStationInfo>> getBandNeighborIndexMap(@NonNull ILadder ladder) {
            // Calculate band neighbourhooods
            final ImmutableTable.Builder<IStationInfo, Band, Set<IStationInfo>> builder = ImmutableTable.builder();
            for (Band band : ladder.getAirBands()) {
                final Map<Station, Set<Integer>> domains = ladder.getStations().stream()
                        .collect(Collectors.toMap(IStationInfo::toSATFCStation, s -> s.getDomain(band)));

                final Map<Station, IStationInfo> stationToInfo = HashBiMap.create(ladder.getStations().stream().collect(Collectors.toMap(s -> s, IStationInfo::toSATFCStation))).inverse();
                final SimpleGraph<IStationInfo, DefaultEdge> constraintGraph = ConstraintGrouper.getConstraintGraph(domains, constraintManager, stationToInfo);
                final NeighborIndex<IStationInfo, DefaultEdge> neighborIndex = new NeighborIndex<>(constraintGraph);
                for (IStationInfo s: stationToInfo.values()) {
                    builder.put(s, band, neighborIndex.neighborsOf(s));
                }
            }
            return builder.build();
        }

        public double computeVacancy(
                @NonNull final IStationInfo station,
                @NonNull final Band band,
                @NonNull final ILadder ladder,
                @NonNull final Map<Integer, Integer> assignment,
                @NonNull final ImmutableTable<IStationInfo, Band, Set<IStationInfo>> bandNeighbourhoods) {
            Preconditions.checkArgument(!band.equals(Band.OFF), "Cannot calculate vacancy for the OFF band.");
            log.trace("Calculating vacancy for station {} on band {}.", station, band);

            // Get active stations which could possibly interfere with station
            Set<IStationInfo> neighbours = bandNeighbourhoods.get(station, band)
                    .stream()
                    .filter(participationRecord::isActive)
                    .filter(neighbour -> ladder.getPossibleMoves(neighbour).contains(band))
                    .filter(neighbour -> ladder.getStationBand(neighbour).isBelow(band))
                    .collect(toImmutableSet());

            if (neighbours.isEmpty()) {
                log.trace("No neighbouring stations to {} below band {}, vacancy is 1.", station, band);
                return 1.0;
            } else {
                double vacancy = 0.;

                for (IStationInfo neighbour : neighbours) {
                    double availableChannels = neighbour.getDomain(band)
                            .stream()
                            .filter(channel -> {
                                // TODO: if you restrict the assignment (I think neighbours of neighbours works?), this could be a less expensive check
                                final Map<Integer, Integer> candidateAssignment = new HashMap<>(assignment);
                                candidateAssignment.put(neighbour.getId(), channel);
                                return feasibilityVerifier.isFeasibleAssignment(candidateAssignment);
                            })
                            .count();
                    availableChannels = Math.max(availableChannels, VAC_FLOOR);
                    vacancy += (availableChannels / BandHelper.toChannels(band).size()) * neighbour.getVolume();
                }

                final double normalizer = neighbours.stream().map(IStationInfo::getVolume).mapToDouble(d -> d).sum();
                vacancy /= normalizer;

                Preconditions.checkState(vacancy > 0 && vacancy <= 1, "Calculated vacancy %s for station %s on band %s is out of domain.", vacancy, station, band);
                return vacancy;
            }
        }
    }
}