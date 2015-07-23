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
package ca.ubc.cs.beta.stationpacking.solvers.underconstrained;

import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import org.jgrapht.alg.NeighborIndex;
import org.jgrapht.graph.DefaultEdge;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.ConstraintGrouper;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Sets;

/**
 * Created by newmanne on 1/8/15.
 */
@Slf4j
public class UnderconstrainedStationFinder implements IUnderconstrainedStationFinder {

    private final IConstraintManager fConstraintManager;

    public UnderconstrainedStationFinder(IConstraintManager aConstraintManger) {
        fConstraintManager = aConstraintManger;
    }


    @Override
    public Set<Station> getUnderconstrainedStations(Map<Station, Set<Integer>> domains) {
        final Set<Station> underconstrainedStations = new HashSet<Station>();

        log.debug("Finding underconstrained stations in the instance...");

        /**
         * Heuristic #1 for underconstrained:
         * For every channel in my domain, is there any channel which, if I go on it, I have no interference constraints with anyone?
         */
        final HashMultimap<Station, Integer> badChannels = HashMultimap.create();
        fConstraintManager.getAllRelevantConstraints(domains).forEach(constraint -> {
            badChannels.put(constraint.getSource(), constraint.getSourceChannel());
            badChannels.put(constraint.getTarget(), constraint.getTargetChannel());
        });

        final NeighborIndex<Station, DefaultEdge> neighborIndex = new NeighborIndex<>(ConstraintGrouper.getConstraintGraph(domains, fConstraintManager));

        for (final Entry<Station, Set<Integer>> domainEntry : domains.entrySet()) {
            final Station station = domainEntry.getKey();
            final Set<Integer> domain = domainEntry.getValue();

            final Set<Integer> stationBadChannels = badChannels.get(station);
            final Set<Integer> stationGoodChannels = Sets.difference(domain, stationBadChannels);

            log.trace("Station {} domain channels: {}.", station, domain);
            log.trace("Station {} bad channels: {}.", station, stationBadChannels);

            if (!stationGoodChannels.isEmpty()) {
                log.trace("Station {} is underconstrained as it has {} domain channels ({}) on which it interferes with no one.", station, stationGoodChannels.size(), stationGoodChannels);
                underconstrainedStations.add(station);
                continue;
            }

            /*
            * Heuristic #2 for underconstrained:
            * For each of my neighbours, count the maximum number of channels in my domain that each neighbour can potentially "block" out. Then assume each neighbour does block out this maximal number of channels. Would I still have a channel left over?
            */
            final long interferingStationsMaxChannelSpread = neighborIndex.neighborsOf(station).stream() // for each neighbour
                    .mapToLong(neighbour -> domains.get(neighbour).stream() // for each channel in the neighbour's domain
                                    .mapToLong(neighbourChannel -> domain.stream() // for each of my channel's
                                            .filter(myChannel -> !fConstraintManager.isSatisfyingAssignment(station, myChannel, neighbour, neighbourChannel))
                                            .count() // count the number of my channels that would be invalid if my neighbour were assigned to neighbourChannel
                                    )
                                    .max() // max over all neighbour's channels
                                    .getAsLong()
                    )
                    .sum();

            if (interferingStationsMaxChannelSpread < domain.size()) {
                log.debug("Station {} is underconstrained as it has {} domain channels, but the neighbouring interfering stations can only spread to a max of {} of them", station, domain.size(), interferingStationsMaxChannelSpread);
                underconstrainedStations.add(station);
            }
        }

        return underconstrainedStations;
    }

}