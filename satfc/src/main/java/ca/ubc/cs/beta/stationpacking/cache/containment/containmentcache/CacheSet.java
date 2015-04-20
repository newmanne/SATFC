package ca.ubc.cs.beta.stationpacking.cache.containment.containmentcache;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A simple cache set entry based on a set.
 * @author afrechet
 * @param <E> - type of elements in cache set entry.
 */
public class CacheSet<E> implements ICacheEntry<E> {

	private final Set<E> fSet;
	
	public CacheSet(Set<E> set)
	{
		fSet = new HashSet<E>(set);
	}
	
	@Override
	public Set<E> getElements() {
		return Collections.unmodifiableSet(fSet);
	}
	
	@Override
	public String toString()
	{
		return fSet.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((fSet == null) ? 0 : fSet.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		@SuppressWarnings("rawtypes")
		CacheSet other = (CacheSet) obj;
		if (fSet == null) {
			if (other.fSet != null)
				return false;
		} else if (!fSet.equals(other.fSet))
			return false;
		return true;
	}

}
