package ca.ubc.cs.beta.stationpacking.cache;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
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
    	// TODO: if we assume a sorted SPI, no need to call toString() method, better to iterate
        return fHashFuction.newHasher()
                .putString(aInstance.toString(), Charsets.UTF_8)
                .hash();
    }
    
    public HashCode rehash(HashCode hash) {
        return fHashFuction.newHasher().putString(hash.toString(), Charsets.UTF_8).hash();
    }


}
