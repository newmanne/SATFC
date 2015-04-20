package ca.ubc.cs.beta.stationpacking.cache.containment.containmentcache;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

/**
 * Organizes cache entries ({@link ICacheEntry}) so that it easy to obtain cache entries that are subsets/supersets
 * of a query cache entry (according to the sets of elements they represent). 
 * 
 * Should support bucketing of cache entries, meaning that if two cache entries represent the same set but are not 
 * equal (according to {@code equals()}), both entries will be contained in the cache.
 * 
 * @author afrechet
 *
 * @param <E> - type of elements in set representing entry.
 * @param <C> - type of cache entry.
 */
public interface IContainmentCache<E,C extends ICacheEntry<E>> {
	
	/**
	 * @param set - set to add to the tree.
	 */
	public void add(C set);

    default void addAll(Collection<C> all) {
        all.forEach(this::add);
    }

	/**
	 * @param set - set to remove from the tree.
	 */
	public void remove(C set);
	
	/**
	 * @param set - set to check for presence in the tree.
	 * @return true if and only if the given set is in the tree.
	 */
	public boolean contains(C set);
	
	/**
	 * @param set - set for which to get all present subsets in the tree.
	 * @return every set currently in the tree that is a subset of the given set.
	 */
	public Iterator<C> getSubsets(C set);
	
	/**
	 * 
	 * @param set - set for which to get the number of present subsets in the tree.
	 * @return the number of subsets present in the tree for the given set.
	 */
	public int getNumberSubsets(C set);
	
	/**
	 * @param set - set for which to get all present supersets in the tree.
	 * @return every set currently in the tree that is a superset of the given set.
	 */
	public Iterator<C> getSupersets(C set);
	
	/**
	 * 
	 * @param set - set for which to get the number of present supersets in the tree.
	 * @return the number of supersets present in the tree for the given set.
	 */
	public int getNumberSupersets(C set);
	
	/**
	 * @return the number of entries currently in the tree.
	 */
	public int size();
	
}
