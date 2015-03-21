package ca.ubc.cs.beta.stationpacking.solvers.decorators.cache;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.cache.ContainmentCache;
import ca.ubc.cs.beta.stationpacking.cache.ContainmentCache.ContainmentCacheEntry;
import ca.ubc.cs.beta.stationpacking.cache.ContainmentCache.ContainmentCacheSATResult;
import ca.ubc.cs.beta.stationpacking.cache.ICacher;
import ca.ubc.cs.beta.stationpacking.cache.ICacher.CacheCoordinate;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

/**
 * Created by newmanne on 01/03/15.
 */
@RequiredArgsConstructor
public class ContainmentCacheProxy {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseServerURL;
    private final CacheCoordinate coordinate;

    @Data
    @AllArgsConstructor
    public static class ContainmentCacheRequest {
        private StationPackingInstance instance;
        private CacheCoordinate coordinate;
    }

    public ContainmentCacheSATResult proveSATBySuperset(StationPackingInstance instance) {
        final UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseServerURL + "/cache/query/SAT");
        final ContainmentCacheRequest request = new ContainmentCacheRequest(instance, coordinate);
        return restTemplate.postForObject(builder.build().toUriString(), request, ContainmentCacheSATResult.class);
    }

//    public ContainmentC findSuperset(BitSet bitSet) {
//        final String interference = "021814SC3M";
//        final int clearingTarget = 32;
//
//        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseServerURL + "/cache")
//                .queryParam("interference", interference)
//                .queryParam("clearingTarget", clearingTarget)
//                .queryParam("query", QueryType.SUBSET.toString());
//
//        final ContainmentCacheResult subsetResult = restTemplate.getForObject(builder.build().toUriString(), ContainmentCacheResult.class);
//        return subsetResult;
//    }
}
