package ca.ubc.cs.beta.stationpacking.cache;

import ca.ubc.cs.beta.stationpacking.cache.ICacher.CacheCoordinate;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import net.jcip.annotations.ThreadSafe;

import java.util.Optional;

/**
 * Created by newmanne on 20/03/15.
 */
@ThreadSafe
public interface ICacheLocator {

    Optional<ContainmentCache> locate(CacheCoordinate coordinate);

}
