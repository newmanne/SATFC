/**
 * Copyright 2015, Auctionomics, Alexandre Fréchette, Kevin Leyton-Brown.
 *
 * This file is part of satfc.
 *
 * satfc is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * satfc is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with satfc.  If not, see <http://www.gnu.org/licenses/>.
 *
 * For questions, contact us at:
 * afrechet@cs.ubc.ca
 */
package ca.ubc.cs.beta.stationpacking.cache;

import ca.ubc.cs.beta.models.fastrf.utils.Hash;
import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.utils.CacheUtils;
import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.*;
import java.util.stream.IntStream;

import static ca.ubc.cs.beta.stationpacking.utils.GuavaCollectors.toImmutableList;

/**
 * Created by newmanne on 1/24/15.
 */
@Slf4j
public class ContainmentCache {

    final ImmutableList<ImmutableList<ContainmentCacheUNSATEntry>> UNSATCache;
    final ImmutableList<ImmutableList<ContainmentCacheSATEntry>> SATCache;

    final ImmutableList<PermutableBitSetComparator> comparators;

    // the first permutation is the default permutation
    final int[][] permutations;

    public ContainmentCache(List<ContainmentCacheSATEntry> SATData, List<ContainmentCacheUNSATEntry> UNSATData) {
        // init permutation
        permutations = initPermutation();

        comparators = Arrays.stream(permutations)
                .map(PermutableBitSetComparator::new)
                .collect(toImmutableList());

        final ImmutableList.Builder<ImmutableList<ContainmentCacheSATEntry>> SATCacheBuilder = ImmutableList.builder();
        final ImmutableList.Builder<ImmutableList<ContainmentCacheUNSATEntry>> UNSATCacheBuilder = ImmutableList.builder();
        for (int i = 0; i < permutations.length; i++) {
            final PermutableBitSetComparator permutableBitSetComparator = comparators.get(i);
            final Comparator<ContainmentCacheSATEntry> SATComparator = (o1, o2) -> permutableBitSetComparator.compare(o1.getBitSet(), o2.getBitSet());
            final Comparator<ContainmentCacheUNSATEntry> UNSATComparator = (o1, o2) -> permutableBitSetComparator.compare(o1.getBitSet(), o2.getBitSet());
            SATData.sort(SATComparator);
            UNSATData.sort(UNSATComparator);
            SATCacheBuilder.add(ImmutableList.copyOf(SATData));
            UNSATCacheBuilder.add(ImmutableList.copyOf(UNSATData));
        }
        SATCache = SATCacheBuilder.build();
        UNSATCache = UNSATCacheBuilder.build();
    }

    private int[][] initPermutation() {
        final int[][] permutationsTemp;
        try {
            final List<String> lines = Resources.readLines(Resources.getResource("precache_permutations.txt"), Charsets.UTF_8);
            final int numPermutations = lines.size();
            permutationsTemp = new int[numPermutations][StationPackingUtils.N_STATIONS];
            for (int i = 0; i < lines.size(); i++) {
                final List<String> numbers = Splitter.on(',').trimResults().splitToList(lines.get(i));
                for (int j = 0; j < numbers.size(); j++) {
                    permutationsTemp[i][j] = Integer.valueOf(numbers.get(j));
                }
            }
            return permutationsTemp;
        } catch (IOException e) {
            throw new RuntimeException("Bad permutations!");
        }
    }

    /**
     * Return a collection of bitsets that are larger than the given bitset according to the permutation of bits where this is the smallest.
     * If we find an exact match, we return that much only
     */
    private Collection<ContainmentCacheSATEntry> smallSetLargerThanOrEqualTo(BitSet aBitSet) {
        final ContainmentCacheSATEntry fakeEntry = new ContainmentCacheSATEntry(aBitSet); // need to wrap this for comparator
        final List<Integer> binarySearchReturn = IntStream
                .range(0, permutations.length)
                .mapToObj(permutationIndex -> Collections.binarySearch(SATCache.get(permutationIndex), fakeEntry, (o1, o2) -> comparators.get(permutationIndex).compare(o1.getBitSet(), o2.getBitSet())))
                .collect(toImmutableList());
        // binary search return value is positive if the item is found in the list (the index). If it's in one list, it will be in all the lists, so might as well just work with the first
        final Integer exactMatchIndexInFirstPermutation = binarySearchReturn.get(0);
        if (exactMatchIndexInFirstPermutation >= 0) {
            return SATCache.get(0).subList(exactMatchIndexInFirstPermutation, exactMatchIndexInFirstPermutation + 1);
        } else {
            final List<Integer> insertionIndices = binarySearchReturn.stream().map(i -> -(i + 1)).collect(toImmutableList());
            log.trace("Insertion indices are {}", insertionIndices);
            final int bestInsertionPoint = Collections.max(insertionIndices);
            final int bestPermutation = insertionIndices.indexOf(bestInsertionPoint);
            final List<ContainmentCacheSATEntry> potentialSupersets = SATCache.get(bestPermutation).subList(bestInsertionPoint, SATCache.get(bestPermutation).size());
            // reverse the list so the bigger things are seen first as they are most likely to be supersets
            return Lists.reverse(potentialSupersets);
        }
    }

    private Collection<ContainmentCacheUNSATEntry> smallSetSmallerThanOrEqualTo(BitSet aBitSet) {
        final ContainmentCacheUNSATEntry fakeEntry = new ContainmentCacheUNSATEntry(aBitSet); // need to wrap this for comparator
        final List<Integer> binarySearchReturn = IntStream
                .range(0, permutations.length)
                .mapToObj(permutationIndex -> Collections.binarySearch(UNSATCache.get(permutationIndex), fakeEntry, (o1, o2) -> comparators.get(permutationIndex).compare(o1.getBitSet(), o2.getBitSet())))
                .collect(toImmutableList());
        final Integer exactMatchIndexInFirstPermutation = binarySearchReturn.get(0);
        if (exactMatchIndexInFirstPermutation >= 0) {
            return UNSATCache.get(0).subList(exactMatchIndexInFirstPermutation, exactMatchIndexInFirstPermutation + 1);
        } else {
            final List<Integer> insertionIndices = binarySearchReturn.stream().map(i -> -(i + 1)).collect(toImmutableList());
            log.trace("Insertion indices are {}", insertionIndices);
            final int bestInsertionPoint = Collections.min(insertionIndices);
            final int bestPermutation = insertionIndices.indexOf(bestInsertionPoint);
            return UNSATCache.get(bestPermutation).subList(0, bestInsertionPoint);
        }
    }

    // true if a is a superset of b
    private boolean isSupersetOrEqualTo(final BitSet a, final BitSet b) {
        return b.stream().allMatch(a::get);
    }

    // true if a is a subset of b
    private boolean isSubsetOrEqualTo(final BitSet a, final BitSet b) {
        return a.stream().allMatch(b::get);
    }

    public ContainmentCacheUNSATResult proveUNSATBySubset(final StationPackingInstance aInstance) {
        // convert instance to bit set representation
        final BitSet bitSet = CacheUtils.toBitSet(aInstance);
        // try to narrow down the entries we have to search by only looking at subsets
        final Collection<ContainmentCacheUNSATEntry> containmentCacheUNSATEntries = smallSetSmallerThanOrEqualTo(bitSet);
        return containmentCacheUNSATEntries.stream()
                .filter(entry -> isSubsetOrEqualTo(bitSet, entry.getBitSet()) && domainIsSubsetOrEqualTo(aInstance.getDomains(), entry.getDomains()))
                .map(entry -> new ContainmentCacheUNSATResult(entry.getKey()))
                .findAny()
                .orElse(ContainmentCacheUNSATResult.failure());
    }

    private boolean domainIsSubsetOrEqualTo(ImmutableMap<Station, Set<Integer>> a, Map<Station, Set<Integer>> b) {
        return a.entrySet().stream().allMatch(entry -> b.get(entry.getKey()).containsAll(entry.getValue()));
    }

    public ContainmentCacheSATResult proveSATBySuperset(final StationPackingInstance aInstance) {
        // convert instance to bit set representation
        final BitSet bitSet = CacheUtils.toBitSet(aInstance);
        // try to narrow down the entries we have to search by only looking at supersets
        final Collection<ContainmentCacheSATEntry> containmentCacheSATEntries = smallSetLargerThanOrEqualTo(bitSet);
        return containmentCacheSATEntries.stream()
                .filter(entry -> isSupersetOrEqualTo(entry.getBitSet(), bitSet) && entry.isSolutionTo(aInstance))
                .map(entry -> new ContainmentCacheSATResult(entry.getAssignmentChannelToStation(), entry.getKey()))
                .findAny()
                .orElse(ContainmentCacheSATResult.failure());
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ContainmentCacheSATResult {

        private Map<Integer, Set<Station>> result;
        // the redis key of the problem whose solution "solves" this problem
        private String key;

        /** true if a solution was found */
        public boolean isValid() {
            return result != null && key != null;
        }

        // return an empty or failed result, that represents an error or that the problem was not solvable via the cache
        public static ContainmentCacheSATResult failure() {
            return new ContainmentCacheSATResult();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContainmentCacheUNSATResult {
        // the redis key of the problem whose solution "solves" this problem
        private String key;

        /** true if a solution was found */
        public boolean isValid() {
            return key != null;
        }

        // return an empty or failed result, that represents an error or that the problem was not solvable via the cache
        public static ContainmentCacheUNSATResult failure() {
            return new ContainmentCacheUNSATResult();
        }
    }

    @Data
    public static class ContainmentCacheSATEntry {
        byte[] channels;
        BitSet bitSet;
        String key;

        // fake constructor
        public ContainmentCacheSATEntry(BitSet bitSet) {
            this.bitSet = bitSet;
        }

        public ContainmentCacheSATEntry(Map<Integer, Set<Station>> answer, String key) {
            this.bitSet = CacheUtils.toBitSet(answer);
            final Map<Station, Integer> stationToChannel = CacheUtils.stationToChannelFromChannelToStation(answer);
            this.key = key;
            final int numStations = this.bitSet.cardinality();
            channels = new byte[numStations];
            int j = 0;
            for (int stationId = bitSet.nextSetBit(0); stationId >= 0; stationId = bitSet.nextSetBit(stationId+1)) {
                channels[j] = stationToChannel.get(new Station(stationId)).byteValue();
                j++;
            }
        }

        // aInstance is already known to be a subset of this entry
        public boolean isSolutionTo(StationPackingInstance aInstance) {
            final ImmutableMap<Station, Set<Integer>> domains = aInstance.getDomains();
            final Map<Integer, Integer> stationToChannel = getAssignment();
            return domains.entrySet().stream().allMatch(entry -> entry.getValue().contains(stationToChannel.get(entry.getKey().getID())));
        }

        @SuppressWarnings("unchecked")
        public Map<Integer, Set<Station>> getAssignmentChannelToStation() {
            final Map<Integer, Integer> stationToChannel = getAssignment();
            final HashMultimap<Integer, Station> channelAssignment = HashMultimap.create();
            stationToChannel.entrySet().forEach(entry -> {
                channelAssignment.get(entry.getValue()).add(new Station(entry.getKey()));
            });
            // safe conversion because of SetMultimap
            return (Map<Integer, Set<Station>>) (Map<?, ?>) channelAssignment.asMap();
        }

        public Map<Integer,Integer> getAssignment() {
            final Map<Integer, Integer> stationToChannel = new HashMap<>();
            int j = 0;
            for (int stationId = bitSet.nextSetBit(0); stationId >= 0; stationId = bitSet.nextSetBit(stationId+1)) {
                stationToChannel.put(stationId, Byte.toUnsignedInt(channels[j]));
                j++;
            }
            return stationToChannel;
        }
    }

    @Data
    public static class ContainmentCacheUNSATEntry {
        final BitSet bitSet;
        Map<Station, Set<Integer>> domains;
        String key;

        // "fake" constructor used for comparator purposes only
        public ContainmentCacheUNSATEntry(BitSet bitSet) {
            this.bitSet = bitSet;
        }

        public ContainmentCacheUNSATEntry(final Map<Station, Set<Integer>> domains, final String key) {
            this.key = key;
            this.domains = domains;
            this.bitSet = new BitSet(StationPackingUtils.N_STATIONS);
            domains.keySet().forEach(station -> bitSet.set(station.getID()));
        }

    }

    /**
     * A comparator that compares bitsets according to an ordering specified by permutation such that the least
     * significant digit is the first number in the permutation)
     */
    public static class PermutableBitSetComparator implements Comparator<BitSet> {

        final int[] permutation;

        public PermutableBitSetComparator(int[] permutation) {
            this.permutation = permutation;
        }

        @Override
        public int compare(BitSet bs1, BitSet bs2) {
            for (int i = permutation.length - 1; i >= 0; i--) {
                int index = permutation[i];
                boolean b1 = bs1.get(index);
                boolean b2 = bs2.get(index);
                if (b1 && !b2) {
                    return 1;
                } else if (!b1 && b2) {
                    return -1;
                }
            }
            return 0;
        }
    }

}