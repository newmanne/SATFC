package ca.ubc.cs.beta.stationpacking.cache;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.cache.ICacher;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.cache.ContainmentCacheProxy;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.cache.ContainmentCacheProxy.ContainmentCacheRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Created by newmanne on 22/03/15.
 */
public class CacherProxy implements ICacher {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseServerURL;
    private final CacheCoordinate coordinate;

    public CacherProxy(String serverURL, CacheCoordinate cacheCoordinate) {
        this.baseServerURL = serverURL;
        this.coordinate = cacheCoordinate;
    }

    @Override
    public void cacheResult(CacheCoordinate cacheCoordinate, StationPackingInstance instance, SolverResult result) {
        final UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseServerURL + "/cache");
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
