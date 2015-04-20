package ca.ubc.cs.beta.stationpacking.cache.containment.containmentcache.decorators;

import ca.ubc.cs.beta.stationpacking.cache.containment.containmentcache.ICacheEntry;
import ca.ubc.cs.beta.stationpacking.cache.containment.containmentcache.IContainmentCache;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Decorator that makes a containment cache thread safe through the use of an read/write lock.
 *
 * @param <E> - type of elements in set representing entry.
 * @param <C> - type of cache entry.
 * @author afrechet
 */
public class ThreadSafeContainmentCacheDecorator<E, C extends ICacheEntry<E>> implements IContainmentCache<E, C> {

    private final IContainmentCache<E, C> fCache;
    private final ReadWriteLock fLock;

    public ThreadSafeContainmentCacheDecorator(IContainmentCache<E, C> cache, ReadWriteLock lock) {
        fCache = cache;
        fLock = lock;
    }

    /**
     * Make the given cache thread safe with a simple unfair reentrant read write lock.
     *
     * @param cache - cache to decorate.
     * @return decorated thread safe cache.
     */
    public static <E, C extends ICacheEntry<E>> ThreadSafeContainmentCacheDecorator<E, C> makeThreadSafe(IContainmentCache<E, C> cache) {
        final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        return new ThreadSafeContainmentCacheDecorator<E, C>(cache, lock);
    }

    /**
     * Lock the decorator's read lock,
     * to allow for a coherent read state.
     */
    public void readLock() {
        fLock.readLock().lock();
    }

    /**
     * Unlock the decorator's read lock.
     */
    public void readUnlock() {
        fLock.readLock().unlock();
    }

    @Override
    public void add(C set) {

        fLock.writeLock().lock();
        try {
            fCache.add(set);
        } finally {
            fLock.writeLock().unlock();
        }
    }

    @Override
    public void remove(C set) {
        fLock.writeLock().lock();
        try {
            fCache.remove(set);
        } finally {
            fLock.writeLock().unlock();
        }
    }

    @Override
    public boolean contains(C set) {
        fLock.readLock().lock();
        try {
            return fCache.contains(set);
        } finally {
            fLock.readLock().unlock();
        }
    }

    @Override
    public Iterator<C> getSubsets(C set) {
        fLock.readLock().lock();
        try {
            return fCache.getSubsets(set);
        } finally {
            fLock.readLock().unlock();
        }
    }

    @Override
    public int getNumberSubsets(C set) {
        fLock.readLock().lock();
        try {
            return fCache.getNumberSubsets(set);
        } finally {
            fLock.readLock().unlock();
        }
    }

    @Override
    public Iterator<C> getSupersets(C set) {
        fLock.readLock().lock();
        try {
            return fCache.getSupersets(set);
        } finally {
            fLock.readLock().unlock();
        }
    }

    @Override
    public int getNumberSupersets(C set) {
        fLock.readLock().lock();
        try {
            return fCache.getNumberSupersets(set);
        } finally {
            fLock.readLock().unlock();
        }
    }

    @Override
    public int size() {
        fLock.readLock().lock();
        try {
            return fCache.size();
        } finally {
            fLock.readLock().unlock();
        }
    }

}
