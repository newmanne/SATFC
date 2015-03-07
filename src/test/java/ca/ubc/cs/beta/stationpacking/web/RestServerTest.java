package ca.ubc.cs.beta.stationpacking.web;

import ca.ubc.cs.beta.stationpacking.solvers.decorators.cache.ContainmentCacheProxy;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

/**
 * Created by newmanne on 01/03/15.
 */
public class RestServerTest {

    @Test
    public void hmf() {
        final ContainmentCacheProxy containmentCacheProxy = new ContainmentCacheProxy("http://localhost:8080/satfcserver");
//        containmentCacheProxy.findSuperset(ImmutableList.of(3, 2));
    }
}
