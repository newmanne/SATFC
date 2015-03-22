package ca.ubc.cs.beta.stationpacking.solvers.decorators.cache;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.cache.ContainmentCache.ContainmentCacheSATResult;
import ca.ubc.cs.beta.stationpacking.cache.ContainmentCache.ContainmentCacheUNSATResult;
import ca.ubc.cs.beta.stationpacking.cache.ICacher.CacheCoordinate;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

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
    @NoArgsConstructor
    public static class ContainmentCacheRequest {
        private StationPackingInstance instance;
        private CacheCoordinate coordinate;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ContainmentCacheCacheRequest {
        private StationPackingInstance instance;
        private CacheCoordinate coordinate;
        private SolverResult result;

    }

    public ContainmentCacheSATResult proveSATBySuperset(StationPackingInstance instance) {
        final UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseServerURL + "/cache/query/SAT");
        final ContainmentCacheRequest request = new ContainmentCacheRequest(instance, coordinate);
        return restTemplate.postForObject(builder.build().toUriString(), request, ContainmentCacheSATResult.class);
    }

    public ContainmentCacheUNSATResult proveUNSATBySubset(StationPackingInstance instance) {
        final UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseServerURL + "/cache/query/UNSAT");
        final ContainmentCacheRequest request = new ContainmentCacheRequest(instance, coordinate);
        return restTemplate.postForObject(builder.build().toUriString(), request, ContainmentCacheUNSATResult.class);
    }

}
