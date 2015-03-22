package ca.ubc.cs.beta.stationpacking.cache;

import ca.ubc.cs.beta.stationpacking.cache.ICacher.CacheCoordinate;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;

import java.util.Optional;

/**
 * Created by newmanne on 20/03/15.
 */
public interface ICacheLocator {

    Optional<ContainmentCache> locate(CacheCoordinate coordinate);

}
