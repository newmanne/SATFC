package ca.ubc.cs.beta.stationpacking.cache.containment.containmentcache.bitset;

import ca.ubc.cs.beta.stationpacking.cache.containment.containmentcache.ICacheEntry;
import ca.ubc.cs.beta.stationpacking.cache.containment.containmentcache.IContainmentCache;

import java.util.*;
import java.util.Map.Entry;

/**
 * A simple bitset containment cache that represents sets as bitsets and uses the integer representation
 * of these bitsets to limit the number of sub/supersets to search. 
 * 
 * Given a query set, we get its bitset representation and corresponding integer number, and then can quickly find
 * all entries with bitset integer number larger (or smaller) than the query's. This allows us to get a superset of all
 * supersets (or subsets), which we can filter to exactly get the sets we are looking for.
 * 
 * @author afrechet
 * @param <E> - the elements the sets.
 * @param <C> - the type of additional content in cache entries.
 */
public class SimpleBitSetCache<E,C extends ICacheEntry<E>> implements IContainmentCache<E,C> {
	
	//The entries of the data structure, hashed by their bitset representation.
	private final Map<BitSet,Set<C>> entries;
	//The tree of bitset, to organize the sub/superset structure.
	private final TreeSet<BitSet> tree;
	//The element permutation to map sets to bitsets.
	private final Map<E,Integer> perm;
	
	public SimpleBitSetCache(Map<E,Integer> permutation)
	{
		if(permutation == null)
		{
			throw new IllegalArgumentException("Cannot create bitset cache with null permutation.");
		}
		
		//Check that permutation is from 0 .. N-1.
		int N = permutation.size()-1;
		Collection<Integer> image = permutation.values();
		for(int i=0;i<=N;i++)
		{
			if(!image.contains(i))
			{
				throw new IllegalArgumentException("Permutation does not map any element to valid index "+i+", must be an invalid permutation.");
			}
		}
		
		perm = new HashMap<E,Integer>(permutation);
		entries = new HashMap<BitSet,Set<C>>();
		tree = new TreeSet<BitSet>(new BitSetComparator());
	}
	
	public SimpleBitSetCache(Set<E> universe)
	{
		if(universe == null)
		{
			throw new IllegalArgumentException("Cannot create "+this.getClass().getSimpleName()+" with empty universe");
		}
		
		perm = new HashMap<E,Integer>(universe.size());
		int index = 0;
		for(E element : universe)
		{
			perm.put(element, index++);
		}
		
		entries = new HashMap<BitSet,Set<C>>();
		tree = new TreeSet<BitSet>(new BitSetComparator());
	}
	
		
	@Override
	public void add(C set) {
		final BitSet bitset = getBitSet(set.getElements());
		
		final Set<C> bitsetentries = entries.getOrDefault(bitset, new HashSet<C>());
		if(bitsetentries.isEmpty())
		{
			tree.add(bitset);
		}
		bitsetentries.add(set);
		entries.put(bitset, bitsetentries);
	}

	@Override
	public void remove(C set) {
		final BitSet bitset = getBitSet(set.getElements());
		
		final Set<C> bitsetentries = entries.get(bitset);
		if(bitsetentries != null)
		{
			bitsetentries.remove(set);			
			if(bitsetentries.isEmpty())
			{
				tree.remove(bitset);
				entries.remove(bitset);
			}
			else
			{
				entries.put(bitset, bitsetentries);
			}
		}
	}

	@Override
	public boolean contains(C set) {
		final BitSet bitset = getBitSet(set.getElements());
		if(entries.containsKey(bitset))
		{
			final Set<C> bitsetentries = entries.get(bitset);
			return bitsetentries.contains(set);
		}
		else
		{
			return false;
		}
		
	}

	@Override
	public Iterator<C> getSubsets(C set) {
		final BitSet bs = getBitSet(set.getElements());
        return tree.headSet(bs, true).stream().filter(smallerbs -> isSubsetOrEqualTo(smallerbs, bs)).map(entries::get).flatMap(Set::stream).iterator();
	}

	@Override
	public int getNumberSubsets(C set) {
		int numsubsets = 0;
		
		BitSet bs = getBitSet(set.getElements());
		for(BitSet smallerbs : tree.headSet(bs, true))
		{
			if(isSubsetOrEqualTo(smallerbs, bs))
			{
				numsubsets+=entries.get(smallerbs).size();
			}
		}
		
		return numsubsets;
	}

	@Override
	public Iterator<C> getSupersets(C set) {
		final BitSet bs = getBitSet(set.getElements());
        return tree.tailSet(bs, true).stream().filter(largerbs -> isSubsetOrEqualTo(bs, largerbs)).map(entries::get).flatMap(Set::stream).iterator();
	}

	@Override
	public int getNumberSupersets(C set) {
		
		int numsupersets = 0;	
		final BitSet bs = getBitSet(set.getElements());
		for(BitSet largerbs : tree.tailSet(bs, true))
		{
			if(isSubsetOrEqualTo(bs, largerbs))
			{
				numsupersets+=entries.get(largerbs).size();
			}
		}
		return numsupersets;
	}

	@Override
	public int size() {
		return tree.size();
	}
	
	/**
	 * A comparator for bitsets that compares based on the integer values of bitsets.
	 * @author newmanne, afrechet
	 */
	private static class BitSetComparator implements Comparator<BitSet>
	{
		@Override
		public int compare(BitSet bs1, BitSet bs2) {
			assert bs1.size() == bs2.size();
			
			for (int i = bs1.size() - 1; i >= 0; i--) {
                boolean b1 = bs1.get(i);
                boolean b2 = bs2.get(i);
                if (b1 && !b2) {
                    return 1;
                } else if (!b1 && b2) {
                    return -1;
                }
            }
            return 0;
		}
	}
	
	/**
	 * @param set - a set of element.
	 * @return the bit set representing the given set of elements, according to the permutation.
	 */
	private BitSet getBitSet(Set<E> set)
	{
		if(set == null)
		{
			throw new IllegalArgumentException("Cannot create bit set out of null set.");
		}
		if(!perm.keySet().containsAll(set))
		{
			throw new IllegalArgumentException("Provided set contains element not in the cache's permutation.");
		}
		
		BitSet b = new BitSet(perm.size());
		for(Entry<E,Integer> permentry : perm.entrySet())
		{
			E element = permentry.getKey();
			int index = permentry.getValue();
			
			if(set.contains(element))
			{
				b.set(index);
			}
		}
		return b;
	}
	
    /**
     * @param bs1 - first bitset.
     * @param bs2 - second bitset.
     * @return true if and only if the set represented by bs1 is a subset of the set represented by bs2.
     */
    private static boolean isSubsetOrEqualTo(final BitSet bs1, final BitSet bs2) {
        return bs1.stream().allMatch(bs2::get);
    }

}
