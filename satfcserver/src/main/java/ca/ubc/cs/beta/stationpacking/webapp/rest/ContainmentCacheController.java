package ca.ubc.cs.beta.stationpacking.webapp.rest;

import ca.ubc.cs.beta.stationpacking.cache.CacherProxy.ContainmentCacheCacheRequest;
import ca.ubc.cs.beta.stationpacking.cache.ContainmentCache;
import ca.ubc.cs.beta.stationpacking.cache.ICacheLocator;
import ca.ubc.cs.beta.stationpacking.cache.RedisCacher;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.cache.ContainmentCacheProxy.ContainmentCacheRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@Slf4j
@RequestMapping("/v1/cache")
public class ContainmentCacheController extends AbstractController {

    @Autowired
    ICacheLocator containmentCache;

    @Autowired
    RedisCacher cacher;

    // note that while this is conceptually a GET request, the fact that we need to send json means that its simpler to achieve as a POST
    @RequestMapping(value = "/query/SAT", method = RequestMethod.POST, produces = JSON_CONTENT)
    @ResponseBody
    public ContainmentCache.ContainmentCacheSATResult lookupSAT(
            @RequestBody final ContainmentCacheRequest instance
    ) {
        final Optional<ContainmentCache> cache = containmentCache.locate(instance.getCoordinate());
        if (cache.isPresent()) {
            return cache.get().proveSATBySuperset(instance.getInstance());
        } else {
            return new ContainmentCache.ContainmentCacheSATResult();
        }
    }

    // note that while this is conceptually a GET request, the fact that we need to send json means that its simpler to achieve as a POST
    @RequestMapping(value = "/query/UNSAT", method = RequestMethod.POST, produces = JSON_CONTENT)
    @ResponseBody
    public ContainmentCache.ContainmentCacheUNSATResult lookupUNSAT(
            @RequestBody final ContainmentCacheRequest instance
    ) {
        final Optional<ContainmentCache> cache = containmentCache.locate(instance.getCoordinate());
        if (cache.isPresent()) {
            return cache.get().proveUNSATBySubset(instance.getInstance());
        } else {
            return new ContainmentCache.ContainmentCacheUNSATResult();
        }
    }

    @RequestMapping(method = RequestMethod.POST)
    @ResponseBody
    public void cache(
            @RequestBody final ContainmentCacheCacheRequest request
    ) {
        cacher.cacheResult(request.getCoordinate(), request.getInstance(), request.getResult());
    }

}