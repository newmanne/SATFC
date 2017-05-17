package ca.ubc.cs.beta.fcc.simulator.vacancy;

import ca.ubc.cs.beta.fcc.simulator.ladder.ILadder;
import ca.ubc.cs.beta.fcc.simulator.participation.ParticipationRecord;
import ca.ubc.cs.beta.fcc.simulator.prices.IPrices;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.fcc.simulator.utils.BandHelper;
import ca.ubc.cs.beta.fcc.simulator.utils.SimulatorUtils;
import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.ConstraintGrouper;
import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.math.util.MathUtils;
import org.jgrapht.alg.NeighborIndex;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ca.ubc.cs.beta.stationpacking.utils.GuavaCollectors.toImmutableSet;

/**
 * Created by newmanne on 2016-07-26.
 */

@Slf4j
public class ParallelVacancyCalculator implements IVacancyCalculator {

    public ParallelVacancyCalculator(ParticipationRecord participation,
                                     IConstraintManager constraintManager,
                                     double feasibilityVacancyContributionFloor,
                                     int nCores
    ) {
        this.nCores = nCores;
        forkJoinPool = new ForkJoinPool(nCores);
        sequentialVacancyCalculator = new SequentialVacancyCalculator(participation, constraintManager, feasibilityVacancyContributionFloor);
    }

    private final SequentialVacancyCalculator sequentialVacancyCalculator;
    private final int nCores;
    private final ForkJoinPool forkJoinPool;

    /**
     * @param ladder     - the auction's ladder structure.
     * @return a map taking each station and band in the ladder to the station's vacancy on the band.
     */
    @Override
    public ImmutableTable<IStationInfo, Band, Double> computeVacancies(
            @NonNull final Collection<IStationInfo> stations,
            @NonNull final ILadder ladder,
            @NonNull final IPrices previousBenchmarkPrices
    ) {
        final ImmutableTable<IStationInfo, Band, Set<IStationInfo>> bandNeighborIndexMap = SimulatorUtils.getBandNeighborIndexMap(ladder, sequentialVacancyCalculator.constraintManager);
        final Map<IStationInfo, Map<Band, Double>> vacancies = new ConcurrentHashMap<>(ladder.getAirBands().size());
        try {
            forkJoinPool.submit(() -> {
                        stations.parallelStream().forEach(station -> {
                            Map<Band, Double> stationVacancies = new EnumMap<>(Band.class);
                            for (Band band : ladder.getAirBands()) {
                                stationVacancies.put(band, sequentialVacancyCalculator.computeVacancy(station, band, ladder, bandNeighborIndexMap, previousBenchmarkPrices));
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
        private final ParticipationRecord participationRecord;
        @NonNull
        private final IConstraintManager constraintManager;

        private final double VAC_FLOOR;

        @Override
        public ImmutableTable<IStationInfo, Band, Double> computeVacancies(@NonNull Collection<IStationInfo> stations, @NonNull ILadder ladder, @NonNull IPrices previousBenchmarkPrices) {
            final ImmutableTable<IStationInfo, Band, Set<IStationInfo>> bandNeighbourhoods = SimulatorUtils.getBandNeighborIndexMap(ladder, constraintManager);
            final ImmutableTable.Builder<IStationInfo, Band, Double> builder = ImmutableTable.builder();
            for (IStationInfo station : stations) {
                for (Band band : ladder.getAirBands()) {
                    final double vacancy = computeVacancy(station, band, ladder, bandNeighbourhoods, previousBenchmarkPrices);
                    builder.put(station, band, vacancy);
                }
            }
            return builder.build();
        }

        public double computeVacancy(
                final IStationInfo station,
                final Band band,
                final ILadder ladder,
                final ImmutableTable<IStationInfo, Band, Set<IStationInfo>> bandNeighbourhoods,
                final IPrices previousBenchmarkPrices) {
            Preconditions.checkArgument(!band.equals(Band.OFF), "Cannot calculate vacancy for the OFF band.");
            log.trace("Calculating vacancy for station {} on band {}.", station, band);

            // If band b is above the pre-auction band of station s and the station's benchmark price for its pre-auction band in the previous round was nonpositive then let V_{t,s,b} = VAC_FLOOR / M_b (where M_b = # channels in b)
            if (band.isAbove(station.getHomeBand()) && previousBenchmarkPrices.getPrice(station, station.getHomeBand()) <= 0.) {
                return VAC_FLOOR / BandHelper.toChannels(band).size();
            }

            // Neighbourhood of s in b defined as set of active stations, including s, that could interfere with s in band b
            Set<IStationInfo> neighbours = Sets.union(bandNeighbourhoods.get(station, band), ImmutableSet.of(station))
                    .stream()
                    .filter(participationRecord::isActive)
                            // G(t,s,b) is all stations in neighbourhood of s in band b whose currently held option is below band b and for which b is a permissible band (permissible means <= pre-auction band)
                    .filter(neighbour -> ladder.getStationBand(neighbour).isBelow(band) && band.isBelowOrEqualTo(neighbour.getHomeBand()))
                    .collect(toImmutableSet());

            if (neighbours.isEmpty()) {
                log.trace("No neighbouring stations to {} below band {}, vacancy is 1.", station, band);
                return 1.0;
            } else {
                double vacancy = 0.;

                for (IStationInfo neighbour : neighbours) {
                    double availableChannels = 0;
                    // Get stations that might interfere with this neighbour on this band
                    final Set<Integer> interferingWithNeighbour = bandNeighbourhoods.get(neighbour, band).stream().map(IStationInfo::getId).collect(Collectors.toSet());
                    // Reduce the tentative assignment so that it only includes these stations, and the neighbour
                    final Map<Integer, Integer> reducedAssignment = Maps.filterKeys(ladder.getPreviousAssignment(), interferingWithNeighbour::contains);
                    // Convert into the format that the constraint manager needs
                    final HashMultimap<Integer, Station> channelToStation = StationPackingUtils.channelToStationFromStationToChannelAsMultimap(reducedAssignment);

                    for (int channel : neighbour.getDomain(band)) {
                        channelToStation.put(channel, neighbour.toSATFCStation());
                        availableChannels += BooleanUtils.toInteger(constraintManager.isSatisfyingAssignment(Multimaps.asMap(channelToStation)));
                        channelToStation.remove(channel, neighbour.toSATFCStation());
                    }

                    availableChannels = Math.max(availableChannels, VAC_FLOOR);
                    vacancy += (availableChannels / BandHelper.toChannels(band).size()) * neighbour.getVolume();
                }

                final double normalizer = neighbours.stream().mapToDouble(IStationInfo::getVolume).sum();
                vacancy /= normalizer;
                Preconditions.checkState(vacancy > 0, "Calculated vacancy %s for station %s on band %s less than 0", vacancy, station, band);
                if (vacancy > 1) {
                    // stupid double rounding can sometimes make 1.00000002 or something
                    Preconditions.checkState(vacancy < 1.001, "Calculated vacancy %s for station %s on band %s greater than 1", vacancy, station, band);
                    vacancy = 1.0;
                }
                return vacancy;
            }
        }
    }
}