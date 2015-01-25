package ca.ubc.cs.beta.stationpacking.database;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.ASolverDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.utils.GuavaCollectors;
import ca.ubc.cs.beta.stationpacking.utils.Watch;
import com.google.common.collect.Lists;

import java.util.*;
import java.util.stream.IntStream;

/**
 * Created by newmanne on 1/24/15.
 * The performance of this class is heavily tied to the choice of BitSet. There are faster alternatives that are worth investigating if this bottlenecks...
 * Also, the memory usage is tied highly to the number of permutations because we need to repeat the SAT and UNSAT caches several times. It's possible to reduce this footprint at the expense of performance.
 * TODO: init method - I propose that a redis key UNSAT-INSTANCES and SAT-INSTANCES just return all the bitvectors. This key can be updated offline by a python script or something
 * TODO: how to go from a match to a result? - the SAT instances should also know their keys
 */
public class PreCache {

    List<List<BitSet>> UNSATCache;
    List<List<BitSet>> SATCache;
    // redis keys for SAT instances
    List<String> SATKeys;

    final List<PermutableBitSetComparator> comparators;
    private final static int NUM_PERMUTATIONS = 5;

    // the first permutation is the default permutation
    int[][] permutations;

    public PreCache() {
        comparators = Lists.newArrayListWithCapacity(NUM_PERMUTATIONS);
        for (int i = 0; i < permutations.length; i++) {
            comparators.add(new PermutableBitSetComparator(permutations[i]));
        }
        // get data from somewhere
        List<BitSet> SATData = null;
        List<BitSet> UNSATData = null;
        for (int i = 0; i < permutations.length; i++) {
            SATData.sort(comparators.get(i));
            UNSATData.sort(comparators.get(i));
            SATCache.add(Lists.newArrayList(SATData));
            UNSATData.add(Lists.newArrayList(SATData));
        }
    }

    private Collection<BitSet> largerThanOrEqualTo(BitSet aBitSet) {
        final List<Integer> insertionIndices = IntStream.range(0, NUM_PERMUTATIONS).mapToObj(permutationIndex -> Collections.binarySearch(SATCache.get(permutationIndex), aBitSet, comparators.get(permutationIndex))).collect(GuavaCollectors.toImmutableList());
        final int bestInsertionPoint = Collections.max(insertionIndices);
        final int bestPermutation = insertionIndices.indexOf(bestInsertionPoint);
        return SATCache.get(bestPermutation).subList(bestInsertionPoint, SATCache.get(bestPermutation).size());
    }

    private Collection<BitSet> smallerThanOrEqualTo(BitSet aBitSet) {
        final List<Integer> insertionIndices = IntStream.range(0, NUM_PERMUTATIONS).mapToObj(permutationIndex -> Collections.binarySearch(UNSATCache.get(permutationIndex), aBitSet, comparators.get(permutationIndex))).collect(GuavaCollectors.toImmutableList());
        final int bestInsertionPoint = Collections.min(insertionIndices);
        final int bestPermutation = insertionIndices.indexOf(bestInsertionPoint);
        return UNSATCache.get(bestPermutation).subList(0, bestInsertionPoint);
    }


    private Optional<BitSet> findSuperset(final BitSet bitSet, Collection<BitSet> cachedBitSets) {
        /*
         * Let A = the instance in question and B = a generic cached instance
         * For every station (ie bit), we want to know that if A has the bit set, then B has the bit set
         * This is exactly logical implication (http://en.wikipedia.org/wiki/Truth_table#Logical_implication) and is equivalent to !a||b at the bit level
         */
        final int length = bitSet.length(); // we only need to check as far as 1's exist in the problem we are looking for
        return cachedBitSets.stream().filter(cachedBitSet -> {
            for (int i = 0; i < length; i++) {
                if (bitSet.get(i) && !cachedBitSet.get(i)) return false;
            }
            return true;
        }).findAny();
    }

    private Optional<BitSet> findSubset(final BitSet bitSet, Collection<BitSet> cachedBitSets) {
        /*
         * Let A = the instance in question and B = a generic cached instance
         * B is a subset of A if for every station (ie bit), if the bit is not set in A, the bit is also not set in B
         * Drawing up a truth table, this corresponds to b||!a for every bit
         */
        return cachedBitSets.stream().filter(cachedBitSet -> {
            for (int i = 0; i < cachedBitSet.length(); i++) {
                if (!bitSet.get(i) && cachedBitSet.get(i)) return false;
            }
            return true;
        }).findAny();
    }

    public Optional<BitSet> findSubset(final BitSet aBitSet) {
        return findSubset(aBitSet, smallerThanOrEqualTo(aBitSet));
    }

    public Optional<String> findSuperset(final BitSet aBitSet) {
        return findSuperset(aBitSet, largerThanOrEqualTo(aBitSet));
    }

    public static class PreCacheResult {
        private final Optional<BitSet>
    }

    public static class PreCacheDecorator extends ASolverDecorator {

        private final PreCache preCache;

        /**
         * @param aSolver - decorated ISolver.
         */
        public PreCacheDecorator(ISolver aSolver, PreCache preCache) {
            super(aSolver);
            this.preCache = preCache;
        }

        @Override
        public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
            Watch watch = Watch.constructAutoStartWatch();
            //BitSet aBitSet = aInstance.toBitSet();
            BitSet aBitSet = null;
            // test unsat cache - if any subset of the problem is UNSAT, then the whole problem is UNSAT
            final Optional<BitSet> subset = preCache.findSubset(aBitSet);
            if (subset.isPresent()) {
                // unsat!
                watch.stop();
                return new SolverResult(SATResult.UNSAT, watch.getElapsedTime());
            }
            // test sat cache - supersets of the problem that are SAT directly correspond to solutions to the current problem!
            final Optional<String> superset = preCache.findSuperset(aBitSet);
            if (superset.isPresent()) {
                // yay! problem is SAT! Now let's look it up
            } else {
                // perhaps we can still find a good place to start a local search by finding a min-hamming distance element in the SAT cache
                return fDecoratedSolver.solve(aInstance, aTerminationCriterion, aSeed);
            }
            return null;
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

//    (bs1, bs2) -> {
//        final BitSet x = ((BitSet) bs1.clone());
//        x.xor(bs2);
//        return (x.isEmpty()) ? 0 : (x.length() == bs2.length() ? 1 : -1);
//    };
}
