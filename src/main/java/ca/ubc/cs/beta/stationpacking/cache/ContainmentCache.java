package ca.ubc.cs.beta.stationpacking.cache;

import ca.ubc.cs.beta.stationpacking.solvers.decorators.cache.IContainmentCache;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import static ca.ubc.cs.beta.stationpacking.utils.GuavaCollectors.toImmutableList;

/**
 * Created by newmanne on 1/24/15.
 * // TODO: lots of duplicated code in this file
 */
@Slf4j
public class ContainmentCache implements IContainmentCache {

    final List<List<ContainmentCacheEntry>> UNSATCache;
    final List<List<ContainmentCacheEntry>> SATCache;

    final List<PermutableBitSetComparator> comparators;

    // the first permutation is the default permutation
    final int[][] permutations;
    final int N_STATIONS = 2173;

    public ContainmentCache(List<ContainmentCacheEntry> SATData, List<ContainmentCacheEntry> UNSATData) {
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
        // get data from somewhere
        for (int i = 0; i < permutations.length; i++) {
            final PermutableBitSetComparator permutableBitSetComparator = comparators.get(i);
            final Comparator<ContainmentCacheEntry> comparator = (o1, o2) -> permutableBitSetComparator.compare(o1.getBitSet(), o2.getBitSet());
            SATData.sort(comparator);
            UNSATData.sort(comparator);
            SATCache.add(new ArrayList<>(SATData));
            UNSATCache.add(new ArrayList<>(UNSATData));
        }
    }

    /**
     * Return a collection of bitsets that are larger than the given bitset according to the permutation of bits where this is the smallest.
     * If we find an exact match, we return that much only
     */
    private BitSetResult smallSetLargerThanOrEqualTo(BitSet aBitSet) {
        final List<Integer> binarySearchReturn = IntStream
                .range(0, permutations.length)
                .mapToObj(permutationIndex -> Collections.binarySearch(SATCache.get(permutationIndex), new ContainmentCacheEntry("", aBitSet), (o1, o2) -> comparators.get(permutationIndex).compare(o1.getBitSet(), o2.getBitSet())))
                .collect(toImmutableList());
        // binary search return value is positive if the item is found in the list (the index). If it's in one list, it will be in all the lists, so might as well just work with the first
        final Integer exactMatchIndexInFirstPermutation = binarySearchReturn.get(0);
        if (exactMatchIndexInFirstPermutation >= 0) {
            return new BitSetResult(SATCache.get(0).subList(exactMatchIndexInFirstPermutation, exactMatchIndexInFirstPermutation + 1), exactMatchIndexInFirstPermutation, 0);
        } else {
            final List<Integer> insertionIndices = binarySearchReturn.stream().map(i -> -(i + 1)).collect(toImmutableList());
            log.trace("Insertion indices are {}", insertionIndices);
            final int bestInsertionPoint = Collections.max(insertionIndices);
            final int bestPermutation = insertionIndices.indexOf(bestInsertionPoint);
            return new BitSetResult(SATCache.get(bestPermutation).subList(bestInsertionPoint, SATCache.get(bestPermutation).size()), bestInsertionPoint, bestPermutation);
        }
    }

    private BitSetResult smallSetSmallerThanOrEqualTo(BitSet aBitSet) {
        final List<Integer> binarySearchReturn = IntStream
                .range(0, permutations.length)
                .mapToObj(permutationIndex -> Collections.binarySearch(UNSATCache.get(permutationIndex), new ContainmentCacheEntry("", aBitSet), (o1, o2) -> comparators.get(permutationIndex).compare(o1.getBitSet(), o2.getBitSet())))
                .collect(toImmutableList());
        final Integer exactMatchIndexInFirstPermutation = binarySearchReturn.get(0);
        if (exactMatchIndexInFirstPermutation >= 0) {
            return new BitSetResult(UNSATCache.get(0).subList(exactMatchIndexInFirstPermutation, exactMatchIndexInFirstPermutation + 1), exactMatchIndexInFirstPermutation, 0);
        } else {
            final List<Integer> insertionIndices = binarySearchReturn.stream().map(i -> -(i + 1)).collect(toImmutableList());
            log.trace("Insertion indices are {}", insertionIndices);
            final int bestInsertionPoint = Collections.min(insertionIndices);
            final int bestPermutation = insertionIndices.indexOf(bestInsertionPoint);
            return new BitSetResult(UNSATCache.get(bestPermutation).subList(0, bestInsertionPoint), bestInsertionPoint, bestPermutation);
        }
    }

    private Optional<ContainmentCacheEntry> findSuperset(final BitSet bitSet, BitSetResult bitSetResult) {
        /*
         * Let A = the instance in question and B = a generic cached instance
         * For every station (ie bit), we want to know that if A has the bit set, then B has the bit set
         * This is exactly logical implication (http://en.wikipedia.org/wiki/Truth_table#Logical_implication) and is equivalent to !a||b at the bit level
         */
        final int length = bitSet.length(); // we only need to check as far as 1's exist in the problem we are looking for
        final AtomicInteger index = new AtomicInteger(bitSetResult.getIndex() + bitSetResult.getCachedResults().size()); // start with what's likeliest to be "big"
        return Lists.reverse(bitSetResult.getCachedResults()).stream().map(ContainmentCacheEntry::getBitSet).filter(cachedBitSet -> {
            index.decrementAndGet();
            for (int i = 0; i < length; i++) {
                if (bitSet.get(i) && !cachedBitSet.get(i)) {
                    return false;
                }
            }
            return true;
        }).findAny().map(superset -> new ContainmentCacheEntry(SATCache.get(bitSetResult.getPermutation()).get(index.get()).getKey(), superset));
    }

    private Optional<ContainmentCacheEntry> findSubset(final BitSet bitSet, BitSetResult bitSetResult) {
        /*
         * Let A = the instance in question and B = a generic cached instance
         * B is a subset of A if for every station (ie bit), if the bit is not set in A, the bit is also not set in B
         * Drawing up a truth table, this corresponds to b||!a for every bit
         */
        final AtomicInteger index = new AtomicInteger(-1);
        return bitSetResult.getCachedResults().stream().map(ContainmentCacheEntry::getBitSet).filter(cachedBitSet -> {
            index.incrementAndGet();
            for (int i = 0; i < cachedBitSet.length(); i++) {
                if (!bitSet.get(i) && cachedBitSet.get(i)) {
                    return false;
                }
            }
            return true;
        }).findAny().map(subset -> new ContainmentCacheEntry(UNSATCache.get(bitSetResult.getPermutation()).get(index.get()).getKey(), subset));
    }

    public ContainmentCacheResult findSubset(final BitSet aBitSet) {
        final Optional<ContainmentCacheEntry> subset = findSubset(aBitSet, smallSetSmallerThanOrEqualTo(aBitSet));
        if (subset.isPresent()) {
            return new ContainmentCacheResult(Optional.of(subset.get().getKey()));
        } else {
            return new ContainmentCacheResult(Optional.<String>empty());
        }
    }

    public ContainmentCacheResult findSuperset(final BitSet aBitSet) {
        final Optional<ContainmentCacheEntry> superset = findSuperset(aBitSet, smallSetLargerThanOrEqualTo(aBitSet));
        if (superset.isPresent()) {
            return new ContainmentCacheResult(Optional.of(superset.get().getKey()));
        } else {
            return new ContainmentCacheResult(Optional.<String>empty());
        }
    }

    @Data
    public static class BitSetResult {
        private final List<ContainmentCacheEntry> cachedResults;
        private final int index;
        private final int permutation;
    }

    @Data
    public static class ContainmentCacheEntry {
        final String key;
        final BitSet bitSet;
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
