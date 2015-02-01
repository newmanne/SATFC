
package ca.ubc.cs.beta.stationpacking.cache;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.metrics.SATFCMetrics;
import ca.ubc.cs.beta.stationpacking.utils.Watch;
import com.google.common.base.Charsets;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

/**
 * Created by newmanne on 1/25/15.
 */
public class StationPackingInstanceHasher {

    // hashing function
    private static final HashFunction fHashFuction = Hashing.murmur3_32();

    public HashCode hash(StationPackingInstance aInstance) {
        final Watch watch = Watch.constructAutoStartWatch();
        final HashCode hash = fHashFuction.newHasher()
                .putString(aInstance.toString(), Charsets.UTF_8)
                .hash();
        SATFCMetrics.postEvent(new SATFCMetrics.TimingEvent(aInstance.getName(), SATFCMetrics.TimingEvent.HASHING, watch.getElapsedTime()));
        return hash;
    }
    
    public HashCode rehash(HashCode hash) {
        return fHashFuction.newHasher().putString(hash.toString(), Charsets.UTF_8).hash();
    }


}
