package ca.ubc.cs.beta.stationpacking.cache;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.cache.IContainmentCache;
import ca.ubc.cs.beta.stationpacking.utils.CacheUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import static ca.ubc.cs.beta.stationpacking.utils.GuavaCollectors.toImmutableList;

/**
 * Created by newmanne on 1/24/15.
 */
@Slf4j
public class ContainmentCache {

    final List<List<ContainmentCacheUNSATEntry>> UNSATCache;
    final List<List<ContainmentCacheSATEntry>> SATCache;

    final List<PermutableBitSetComparator> comparators;

    // the first permutation is the default permutation
    final int[][] permutations;
    final int N_STATIONS = 2173;

    public ContainmentCache(List<ContainmentCacheSATEntry> SATData, List<ContainmentCacheUNSATEntry> UNSATData) {
        // init permutation
        try {
            final List<String> lines = Resources.readLines(Resources.getResource("precache_permutations.txt"), Charsets.UTF_8);
            final int numPermutations = lines.size();
            permutations = new int[numPermutations][N_STATIONS];
            for (int i = 0; i < lines.size(); i++) {
                final List<String> numbers = Splitter.on(',').trimResults().splitToList(lines.get(i));
                for (int j = 0; j < numbers.size(); j++) {
                    permutations[i][j] = Integer.valueOf(numbers.get(j));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Bad permutations!");
        }

        comparators = new ArrayList<>(permutations.length);
        for (int[] permutation : permutations) {
            comparators.add(new PermutableBitSetComparator(permutation));
        }
        UNSATCache = new ArrayList<>(permutations.length);
        SATCache = new ArrayList<>(permutations.length);
        for (int i = 0; i < permutations.length; i++) {
            final PermutableBitSetComparator permutableBitSetComparator = comparators.get(i);
            final Comparator<ContainmentCacheSATEntry> SATComparator = (o1, o2) -> permutableBitSetComparator.compare(o1.getBitSet(), o2.getBitSet());
            final Comparator<ContainmentCacheUNSATEntry> UNSATComparator = (o1, o2) -> permutableBitSetComparator.compare(o1.getBitSet(), o2.getBitSet());
            SATData.sort(SATComparator);
            UNSATData.sort(UNSATComparator);
            SATCache.add(new ArrayList<>(SATData));
            UNSATCache.add(new ArrayList<>(UNSATData));
        }
    }

    /**
     * Return a collection of bitsets that are larger than the given bitset according to the permutation of bits where this is the smallest.
     * If we find an exact match, we return that much only
     */
    private Collection<ContainmentCacheSATEntry> smallSetLargerThanOrEqualTo(BitSet aBitSet) {
        final ContainmentCacheSATEntry fakeEntry = new ContainmentCacheSATEntry();
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
            return SATCache.get(bestPermutation).subList(bestInsertionPoint, SATCache.get(bestPermutation).size());
        }
    }

//    private BitSetResult smallSetSmallerThanOrEqualTo(BitSet aBitSet) {
//        final List<Integer> binarySearchReturn = IntStream
//                .range(0, permutations.length)
//                .mapToObj(permutationIndex -> Collections.binarySearch(UNSATCache.get(permutationIndex), new ContainmentCacheEntry("", aBitSet), (o1, o2) -> comparators.get(permutationIndex).compare(o1.getBitSet(), o2.getBitSet())))
//                .collect(toImmutableList());
//        final Integer exactMatchIndexInFirstPermutation = binarySearchReturn.get(0);
//        if (exactMatchIndexInFirstPermutation >= 0) {
//            return new BitSetResult(UNSATCache.get(0).subList(exactMatchIndexInFirstPermutation, exactMatchIndexInFirstPermutation + 1), exactMatchIndexInFirstPermutation, 0);
//        } else {
//            final List<Integer> insertionIndices = binarySearchReturn.stream().map(i -> -(i + 1)).collect(toImmutableList());
//            log.trace("Insertion indices are {}", insertionIndices);
//            final int bestInsertionPoint = Collections.min(insertionIndices);
//            final int bestPermutation = insertionIndices.indexOf(bestInsertionPoint);
//            return new BitSetResult(UNSATCache.get(bestPermutation).subList(0, bestInsertionPoint), bestInsertionPoint, bestPermutation);
//        }
//    }

    // true if a is a superset of b
    private boolean isSuperset(final BitSet a, final BitSet b) {
        final int length = b.length(); // we only need to check as far as 1's exist in the problem we are looking for
        for (int i = 0; i < length; i++) {
            if (b.get(i) && !a.get(i)) {
                return false;
            }
        }
        return true;
    }

//    private Optional<ContainmentCacheEntry> findSubset(final BitSet bitSet, BitSetResult bitSetResult) {
//        /*
//         * Let A = the instance in question and B = a generic cached instance
//         * B is a subset of A if for every station (ie bit), if the bit is not set in A, the bit is also not set in B
//         * Drawing up a truth table, this corresponds to b||!a for every bit
//         */
//        final AtomicInteger index = new AtomicInteger(-1);
//        return bitSetResult.getCachedResults().stream().map(ContainmentCacheEntry::getBitSet).filter(cachedBitSet -> {
//            index.incrementAndGet();
//            for (int i = 0; i < cachedBitSet.length(); i++) {
//                if (!bitSet.get(i) && cachedBitSet.get(i)) {
//                    return false;
//                }
//            }
//            return true;
//        }).findAny().map(subset -> new ContainmentCacheEntry(UNSATCache.get(bitSetResult.getPermutation()).get(index.get()).getKey(), subset));
//    }
//
//    public ContainmentCacheSATResult findSubset(final BitSet aBitSet) {
//        final Optional<ContainmentCacheEntry> subset = findSubset(aBitSet, smallSetSmallerThanOrEqualTo(aBitSet));
//        if (subset.isPresent()) {
//            return new ContainmentCacheResult(Optional.of(subset.get().getKey()));
//        } else {
//            return new ContainmentCacheResult(Optional.<String>empty());
//        }
//    }

    public ContainmentCacheSATResult proveSATBySuperset(final StationPackingInstance aInstance) {
        final BitSet bitSet = CacheUtils.toBitSet(aInstance);
        final Collection<ContainmentCacheSATEntry> containmentCacheSATEntries = smallSetLargerThanOrEqualTo(bitSet);
        final Optional<ContainmentCacheSATEntry> solution = containmentCacheSATEntries.stream().filter(entry -> isSuperset(entry.getBitSet(), bitSet) && entry.isSolutionTo(aInstance)).findAny();
        if (solution.isPresent()) {
            return new ContainmentCacheSATResult(Optional.of(solution.get().getAssignmentChannelToStation()));
        } else {
            return new ContainmentCacheSATResult(Optional.empty());
        }
    }

    @Data
    public static class ContainmentCacheSATResult {
        private final Optional<Map<Integer, Set<Station>>> result;
    }

    @Data
    public static class ContainmentCacheUNSATResult {
        private final Optional<SATResult> unsat;
    }

    @Data
    public static class ContainmentCacheSATEntry {
        byte[] channels;
        final BitSet bitSet;

        // fake constructor
        public ContainmentCacheSATEntry(BitSet bitSet) {
            this.bitSet = bitSet;
        }

        public ContainmentCacheSATEntry(Map<Integer, Set<Station>> answer) {
            this.bitSet = CacheUtils.toBitSet(answer);
            final int numStations = this.bitSet.cardinality();
            channels = new byte[numStations];
            // TODO:
//            int j = 0;
//            for (int stationId = bitSet.nextSetBit(0); stationId >= 0; stationId = bitSet.nextSetBit(stationId+1)) {
//                channels[j] = a
//                j++;
//            }

        }

        // aInstance is already known to be a subset of this entry
        public boolean isSolutionTo(StationPackingInstance aInstance) {
            final ImmutableMap<Station, Set<Integer>> domains = aInstance.getDomains();
            final Map<Integer, Integer> stationToChannel = getAssignment();
            return domains.entrySet().stream().allMatch(entry -> entry.getValue().contains(stationToChannel.get(entry.getKey().getID())));
        }

        public Map<Integer, Set<Station>> getAssignmentChannelToStation() {
            final Map<Integer, Integer> stationToChannel = getAssignment();
            final Map<Integer, Set<Station>> channelAssignment = new HashMap<>();
            stationToChannel.entrySet().forEach(entry -> {
                if (!channelAssignment.containsKey(entry.getValue())) {
                    channelAssignment.put(entry.getValue(), new HashSet<>());
                }
                channelAssignment.get(entry.getValue()).add(new Station(entry.getKey()));
            });
            return channelAssignment;
        }

        public Map<Integer,Integer> getAssignment() {
            final Map<Integer, Integer> stationToChannel = new HashMap<>();
            int j = 0;
            for (int stationId = bitSet.nextSetBit(0); stationId >= 0; stationId = bitSet.nextSetBit(stationId+1)) {
                stationToChannel.put(stationId, Byte.toUnsignedInt(channels[stationId]));
                j++;
            }
            return stationToChannel;
        }
    }

    @Data
    public static class ContainmentCacheUNSATEntry {
        final BitSet bitSet;
        final Map<Station, Set<Integer>> domain;

        public boolean isSolutionTo(StationPackingInstance aInstance) {
            return false;
        }
    }

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
