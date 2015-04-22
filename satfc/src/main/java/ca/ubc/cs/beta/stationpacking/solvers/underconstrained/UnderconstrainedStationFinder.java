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

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

@Slf4j
/**
 * Note that while the stations returned by this algorithm are underconstrained,
 * this algorithm is simplistic and may leave out stations that are underconstrained
 */
public class UnderconstrainedStationFinder implements IUnderconstrainedStationFinder {

    private final IConstraintManager fConstraintManager;

    public UnderconstrainedStationFinder(IConstraintManager aConstraintManger) {
        fConstraintManager = aConstraintManger;
    }

    @Override
    public Set<Station> getUnderconstrainedStations(Map<Station, Set<Integer>> domains) {
        //Find  the under constrained nodes in the instance.
        final Set<Station> underconstrainedStations = new HashSet<>();

        log.debug("Finding underconstrained stations in the instance...");

        final HashMultimap<Station, Integer> badChannels = HashMultimap.create();
        for (final Entry<Station, Set<Integer>> domainEntry : domains.entrySet()) {
            final Station station = domainEntry.getKey();
            final Set<Integer> domain = domainEntry.getValue();

            // If a station interferes with another station on a channel, call that channel a bad channel
            for (Integer domainChannel : domain) {
                final Set<Station> interferingStations = Sets.union(fConstraintManager.getCOInterferingStations(station, domainChannel), fConstraintManager.getADJplusInterferingStations(station, domainChannel));
                for (Station interferingStation: interferingStations) {
                    if (domains.keySet().contains(interferingStation) && domains.get(interferingStation).contains(domainChannel)) {
                        badChannels.put(station, domainChannel);
                        badChannels.put(interferingStation, domainChannel);
                    }
                }
            }
        }

        for (final Entry<Station, Set<Integer>> domainEntry : domains.entrySet()) {
            final Station station = domainEntry.getKey();
            final Set<Integer> domain = domainEntry.getValue();

            final Set<Integer> stationBadChannels = badChannels.get(station);
            final Set<Integer> stationGoodChannels = Sets.difference(domain, stationBadChannels);

            log.trace("Station {} domain channels: {}.", station, domain);
            log.trace("Station {} bad channels: {}.", station, stationBadChannels);

            if (!stationGoodChannels.isEmpty()) {
                log.trace("Station {} is underconstrained has it has {} domain channels ({}) on which it interferes with no one.", station, stationGoodChannels.size(), stationGoodChannels);
                underconstrainedStations.add(station);
            }
        }
        return underconstrainedStations;
    }

}