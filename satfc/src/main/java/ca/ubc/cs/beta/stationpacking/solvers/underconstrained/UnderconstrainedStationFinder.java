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

import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.alg.NeighborIndex;
import org.jgrapht.graph.DefaultEdge;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.ConstraintGrouper;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Sets;

/**
 * Created by newmanne on 1/8/15.
 * <p/>
 * The goal is to find stations for which, no matter how their neighbours are arranged, there will always be a channel
 * to put them onto. Then, you can remove them from the problem, and simply add them back afterwards by iterating
 * through their domain until you find a satisfying assignment.
 * <p/>
 * One helpful framework for thinking about this problem is to think of the question as:
 * If all of my neighbours were placed adversarially to block out the maximum number of channels from my domain,
 * how many could they block out? If the answer is less than my domain's size, then I am underconstrained.
 * <p/>
 * For example,
 * Neighbour A can block out {1, 2} or {2, 3}
 * Neighbour B can block out {2} or {2, 3}
 * Then the worst case is when neighbour A blocks out {1,2} and neighbour B blocks out {2,3}
 * <p/>
 * Slightly more formally:
 * There are N groups of sets
 * You have to choose exactly one set from each group
 * Your goal is to maximize the size of the union of the groups that you choose
 * (Note that we don't need the actual values of the choices, just the size)
 * <p/>
 * This problem seems to be a variant of the Maximum Coverage Problem
 * <p/>
 * We do not solve the problem exactly, but rely instead take the minimum of two heuristics that are upper bounds to this question
 * 1) The size of the union of all the sets in every group
 * 2) The sum of the sizes of the largest set in every group
 */
@Slf4j
public class UnderconstrainedStationFinder implements IUnderconstrainedStationFinder {

    private final IConstraintManager constraintManager;
    private final boolean performExpensiveAnalysis;

    public UnderconstrainedStationFinder(IConstraintManager constraintManager, boolean performExpensiveAnalysis) {
        this.constraintManager = constraintManager;
        this.performExpensiveAnalysis = performExpensiveAnalysis;
    }

    public static class SNode {
    	@Getter
        private final Set<Station> stations;
    	@Getter
        private final Set<Set<Integer>> channelsToBlockOut;
    	@Getter
    	private final int score;
        
        public SNode(Set<Station> stations, Set<Set<Integer>> channelsToBlockOut) {
        	this.stations = stations;
        	this.channelsToBlockOut = channelsToBlockOut;
        	this.score = channelsToBlockOut.stream().mapToInt(Set::size).max().getAsInt();
		}

        public SNode merge(SNode other) {
            // TODO: restrict invalid combinations of channels during a merge (and think more... this definitely works for 2 stations, but harder to see how to do it for larger merges)
            return new SNode(Sets.union(stations, other.getStations()), cartesianProduct(channelsToBlockOut, other.getChannelsToBlockOut()));
        }

        private Set<Set<Integer>> cartesianProduct(Set<Set<Integer>> a, Set<Set<Integer>> b) {
            final Set<Set<Integer>> product = new HashSet<>();
            for (Set<Integer> sA : a) {
                for (Set<Integer> sB : b) {
                    product.add(Sets.union(sA, sB));
                }
            }
            return product;
        }

    }

    @Override
    public Set<Station> getUnderconstrainedStations(Map<Station, Set<Integer>> domains, ITerminationCriterion terminationCriterion) {
        final Set<Station> underconstrainedStations = new HashSet<Station>();

        log.debug("Finding underconstrained stations in the instance...");

        final NeighborIndex<Station, DefaultEdge> neighborIndex = new NeighborIndex<>(ConstraintGrouper.getConstraintGraph(domains, constraintManager));
        for (final Entry<Station, Set<Integer>> domainEntry : domains.entrySet()) {
            final Station station = domainEntry.getKey();
            final Set<Integer> domain = domainEntry.getValue();
            final Set<Station> neighbours = neighborIndex.neighborsOf(station);
            log.info("Station {} has {} neighbours, {}", station, neighbours.size(), neighbours);
            if (!neighbours.isEmpty() && neighbours.size() <= 15) {
                // Fill up the initial map
                final Map<Set<Station>, SNode> channelsThatANeighbourCanBlockOut = neighbours
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        ImmutableSet::of,
                                        neighbour -> new SNode(ImmutableSet.of(neighbour), domains.get(neighbour)
                                                .stream() // for each channel in the neighbour's domain
                                                .map(neighbourChannel -> domain
                                                                .stream() // for each of my channel's
                                                                .filter(myChannel -> !constraintManager.isSatisfyingAssignment(station, myChannel, neighbour, neighbourChannel))  // count the number of my channels that would be invalid if my neighbour were assigned to neighbourChannel
                                                                .collect(Collectors.toSet())
                                                ).collect(Collectors.toSet()))
                                )
                        );
                final List<SNode> candidates = channelsThatANeighbourCanBlockOut.values().stream().collect(Collectors.toList());
                while (candidates.size() > 1) {
                    // Compute all of the possible merges
                    int maxScoreReduction = Integer.MIN_VALUE;
                    SNode del1 = null;
                    SNode del2 = null;
                    for (int i = 0; i < candidates.size(); i++) {
                        final SNode sNode1 = candidates.get(i);
                        for (int j = i + 1; j < candidates.size(); j++) {
                            final SNode sNode2 = candidates.get(j);
                            final SNode merge = channelsThatANeighbourCanBlockOut.computeIfAbsent(Sets.union(sNode1.getStations(), sNode2.getStations()), unused -> sNode1.merge(sNode2));
                            // TODO: can still do better by avoid all of this- the min hasn't changed unless you hit it!
                            final int scoreReduction = sNode1.getScore() + sNode2.getScore() - merge.getScore();
                            if (scoreReduction > maxScoreReduction) {
                                maxScoreReduction = scoreReduction;
                                del1 = sNode1;
                                del2 = sNode2;
                            }
                        }
                    }
                    log.info("Largest score reduction is {}, by merging nodes {} and {}", maxScoreReduction, del1.getStations(), del2.getStations());
                    final SNode sNode = channelsThatANeighbourCanBlockOut.get(Sets.union(del1.getStations(), del2.getStations()));
                    candidates.removeAll(Arrays.asList(del1, del2));
                    candidates.add(sNode);
                }
                final int maxSpread = Iterables.getOnlyElement(candidates).getScore();
                log.info("Max spread is upper bounded at {}, and the domain is of size {}", maxSpread, domain.size());
                if (maxSpread < domain.size()) {
                    log.info("Station {} is underconstrained as it has {} domain channels, but the neighbouring interfering stations can only spread to a max of {} of them", station, domain.size(), maxSpread);
                    underconstrainedStations.add(station);
                }

            }
        }
//
//
//        /**
//         * Heuristic #1 for underconstrained:
//         * Take the union of all the channels that my neighbours can block and see if its smaller than my domain
//         */
//        final HashMultimap<Station, Integer> badChannels = HashMultimap.create();
//        constraintManager.getAllRelevantConstraints(domains).forEach(constraint -> {
//            badChannels.put(constraint.getSource(), constraint.getSourceChannel());
//            badChannels.put(constraint.getTarget(), constraint.getTargetChannel());
//        });
//        for (final Entry<Station, Set<Integer>> domainEntry : domains.entrySet()) {
//            if (terminationCriterion.hasToStop()) {
//                log.debug("Underconstrained stations timed out. Returned set will be only a partial set");
//                break;
//            }
//            final Station station = domainEntry.getKey();
//            final Set<Integer> domain = domainEntry.getValue();
//
//            final Set<Integer> stationBadChannels = badChannels.get(station);
//            final Set<Integer> stationGoodChannels = Sets.difference(domain, stationBadChannels);
//
//            log.trace("Station {} domain channels: {}.", station, domain);
//            log.trace("Station {} bad channels: {}.", station, stationBadChannels);
//
//            if (!stationGoodChannels.isEmpty()) {
//                log.trace("Station {} is underconstrained as it has {} domain channels ({}) on which it interferes with no one.", station, stationGoodChannels.size(), stationGoodChannels);
//                underconstrainedStations.add(station);
//                continue;
//            }
//
//            if (performExpensiveAnalysis) {
//                /*
//                 * Heuristic #2 for underconstrained:
//                 * For each of my neighbours, count the maximum number of channels in my domain that each neighbour can potentially "block" out. Then assume each neighbour does block out this maximal number of channels. Would I still have a channel left over?
//                 */
//                final Set<Station> neighbours = neighborIndex.neighborsOf(station);
//                if (neighbours.size() >= domain.size()) {
//                    log.trace("Station {} has {} neighbours but only {} channels, so the channel counting heuristic will not work", station, neighbours.size(), domain.size());
//                    continue;
//                }
//                final long interferingStationsMaxChannelSpread = neighbours.stream() // for each neighbour
//                        .mapToLong(neighbour -> domains.get(neighbour).stream() // for each channel in the neighbour's domain
//                                        .mapToLong(neighbourChannel -> domain.stream() // for each of my channel's
//                                                .filter(myChannel -> !constraintManager.isSatisfyingAssignment(station, myChannel, neighbour, neighbourChannel))
//                                                .count() // count the number of my channels that would be invalid if my neighbour were assigned to neighbourChannel
//                                        )
//                                        .max() // max over all neighbour's channels
//                                        .getAsLong()
//                        )
//                        .sum();
//
//                if (interferingStationsMaxChannelSpread < domain.size()) {
//                    log.debug("Station {} is underconstrained as it has {} domain channels, but the neighbouring interfering stations can only spread to a max of {} of them", station, domain.size(), interferingStationsMaxChannelSpread);
//                    underconstrainedStations.add(station);
//                }
//            }
//        }

        return underconstrainedStations;
    }

}