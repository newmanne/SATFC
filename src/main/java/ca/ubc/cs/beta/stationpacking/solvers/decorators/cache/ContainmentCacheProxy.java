package ca.ubc.cs.beta.stationpacking.solvers.decorators.cache;

import ca.ubc.cs.beta.stationpacking.cache.ContainmentCache.ContainmentCacheEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

/**
 * Created by newmanne on 01/03/15.
 */
@RequiredArgsConstructor
public class ContainmentCacheProxy implements IContainmentCache {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseServerURL;

    @Override
    public ContainmentCacheResult findSubset(List<Integer> stations) {
        final String interference = "021814SC3M";
        final int clearingTarget = 32;

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseServerURL + "/cache")
                .queryParam("interference", interference)
                .queryParam("clearingTarget", clearingTarget)
                .queryParam("query", QueryType.SUPERSET.toString())
                .queryParam("stations", stations);

        final ContainmentCacheResult supersetResult = restTemplate.getForObject(builder.build().toUriString(), ContainmentCacheResult.class);
        return supersetResult;
    }

    @Override
    public ContainmentCacheResult findSuperset(List<Integer> stations) {
        final String interference = "021814SC3M";
        final int clearingTarget = 32;

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseServerURL + "/cache")
                .queryParam("interference", interference)
                .queryParam("clearingTarget", clearingTarget)
                .queryParam("query", QueryType.SUBSET.toString())
                .queryParam("bitString", stations);

        final ContainmentCacheResult subsetResult = restTemplate.getForObject(builder.build().toUriString(), ContainmentCacheResult.class);
        return subsetResult;
    }
}
