package ca.ubc.cs.beta.stationpacking.cache;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.cache.ICacher;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.cache.ContainmentCacheProxy;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.cache.ContainmentCacheProxy.ContainmentCacheRequest;
import ca.ubc.cs.beta.stationpacking.utils.CacheUtils;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Created by newmanne on 22/03/15.
 */
@Slf4j
public class CacherProxy implements ICacher {

    private final RestTemplate restTemplate = CacheUtils.getRestTemplate();
    private final String baseServerURL;
    private final CacheCoordinate coordinate;

    public CacherProxy(String serverURL, CacheCoordinate cacheCoordinate) {
        this.baseServerURL = serverURL;
        this.coordinate = cacheCoordinate;
    }

    @Override
    public void cacheResult(CacheCoordinate cacheCoordinate, StationPackingInstance instance, SolverResult result) {
        final UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseServerURL + "/v1/cache");
        final ContainmentCacheCacheRequest request = new ContainmentCacheCacheRequest(instance, coordinate, result);
        restTemplate.postForLocation(builder.build().toUriString(), request);
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ContainmentCacheCacheRequest {
        private StationPackingInstance instance;
        private CacheCoordinate coordinate;
        private SolverResult result;

    }


}
