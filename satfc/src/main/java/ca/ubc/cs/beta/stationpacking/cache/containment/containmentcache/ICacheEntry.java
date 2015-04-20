package ca.ubc.cs.beta.stationpacking.cache.containment.containmentcache;

import java.util.Set;

/**
 * An abstraction for the queries to a containment cache.
 * @author afrechet
 *
 * @param <E> - elements available in a containment cache.
 */
public interface ICacheEntry<E> {
	
	/**
	 * @return the set element to which the cache corresponds.
	 */
	public Set<E> getElements();
	
}
