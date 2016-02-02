/**
 * Copyright 2015, Auctionomics, Alexandre Fréchette, Neil Newman, Kevin Leyton-Brown.
 *
 * This file is part of SATFC.
 *
 * SATFC is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SATFC is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SATFC.  If not, see <http://www.gnu.org/licenses/>.
 *
 * For questions, contact us at:
 * afrechet@cs.ubc.ca
 */
package ca.ubc.cs.beta.stationpacking.webapp.rest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;

import ca.ubc.cs.beta.stationpacking.cache.ICacheScreener;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.webapp.SATFCServerApplication;
import ca.ubc.cs.beta.stationpacking.webapp.parameters.SATFCServerParameters;
import lombok.extern.slf4j.Slf4j;

import org.apache.catalina.connector.ClientAbortException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.cache.ICacheLocator;
import ca.ubc.cs.beta.stationpacking.cache.RedisCacher;
import ca.ubc.cs.beta.stationpacking.cache.containment.ContainmentCacheSATEntry;
import ca.ubc.cs.beta.stationpacking.cache.containment.ContainmentCacheSATResult;
import ca.ubc.cs.beta.stationpacking.cache.containment.ContainmentCacheUNSATEntry;
import ca.ubc.cs.beta.stationpacking.cache.containment.ContainmentCacheUNSATResult;
import ca.ubc.cs.beta.stationpacking.cache.containment.containmentcache.ISatisfiabilityCache;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.cache.ContainmentCacheProxy.ContainmentCacheRequest;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.RatioGauge;
import com.codahale.metrics.Timer;

@Controller
@Slf4j
@RequestMapping("/v1/cache")
public class ContainmentCacheController {

    private final String JSON_CONTENT = "application/json";

    @Autowired
    ICacheLocator containmentCacheLocator;

    @Autowired
    RedisCacher cacher;

    @Autowired
    SATFCServerParameters parameters;

    @Autowired
    ICacheScreener screener;

    // Metrics
    @Autowired
    MetricRegistry registry;
    private Meter cacheAdditions;
    private Meter satCacheHits;
    private Timer satCacheTimer;
    private Meter unsatCacheHits;
    private Timer unsatCacheTimer;

    private volatile Map<Integer, Set<Station>> lastCachedAssignment = new HashMap<>();

    @PostConstruct
    void init() {
        cacheAdditions = registry.meter("cache.sat.additions");
        satCacheHits = registry.meter("cache.sat.hits");
        satCacheTimer = registry.timer("cache.sat.timer");
        unsatCacheHits = registry.meter("cache.unsat.hits");
        unsatCacheTimer = registry.timer("cache.unsat.timer");
        registry.register("cache.sat.hitrate.fifteenminute", new RatioGauge() {
            @Override
            protected Ratio getRatio() {
                return Ratio.of(satCacheHits.getFifteenMinuteRate(), satCacheTimer.getFifteenMinuteRate());
            }
        });
        registry.register("cache.unsat.hitrate.fifteenminute", new RatioGauge() {
            @Override
            protected Ratio getRatio() {
                return Ratio.of(unsatCacheHits.getFifteenMinuteRate(), unsatCacheTimer.getFifteenMinuteRate());
            }
        });
        registry.register("cache.sat.hitrate.overall", new RatioGauge() {
            @Override
            protected Ratio getRatio() {
                return Ratio.of(satCacheHits.getCount(), satCacheTimer.getCount());
            }
        });
        registry.register("cache.unsat.hitrate.overall", new RatioGauge() {
            @Override
            protected Ratio getRatio() {
                return Ratio.of(unsatCacheHits.getCount(), unsatCacheTimer.getCount());
            }
        });
    }

    @ExceptionHandler(ClientAbortException.class)
    void clientAbortException() {
        // Nothing to do
        log.trace("ClientAbortException ignored because it is expected behavior");
    }

    // note that while this is conceptually a GET request, the fact that we need to send json means that its simpler to achieve as a POST
    @RequestMapping(value = "/query/SAT", method = RequestMethod.POST, produces = JSON_CONTENT, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ContainmentCacheSATResult lookupSAT(
            @RequestBody final ContainmentCacheRequest request
    ) {
        final Timer.Context context = satCacheTimer.time();
        try {
            final StationPackingInstance instance = request.getInstance();
            final String description = instance.hasName() ? instance.getName() : instance.getInfo();
            log.info("Querying the SAT cache with coordinate {} for entry {}", request.getCoordinate(), description);
            final ISatisfiabilityCache cache = containmentCacheLocator.locate(request.getCoordinate());
            final ContainmentCacheSATResult containmentCacheSATResult;
            if (parameters.isExcludeSameAuction()) {
                final String auction = instance.getAuction();
                containmentCacheSATResult = cache.proveSATBySuperset(instance, auction);
            } else {
                containmentCacheSATResult = cache.proveSATBySuperset(instance);
            }
            if (containmentCacheSATResult.isValid()) {
                log.info("Query for SAT cache with coordinate {} for entry {} is a hit", request.getCoordinate(), description);
                satCacheHits.mark();
            } else {
                log.info("Query for SAT cache with coordinate {} for entry {} is a miss", request.getCoordinate(), description);
            }
            return containmentCacheSATResult;
        } finally {
            context.stop();
        }
    }


    // note that while this is conceptually a GET request, the fact that we need to send json means that its simpler to achieve as a POST
    @RequestMapping(value = "/query/UNSAT", method = RequestMethod.POST, produces = JSON_CONTENT, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ContainmentCacheUNSATResult lookupUNSAT(
            @RequestBody final ContainmentCacheRequest request
    ) {
        final Timer.Context context = unsatCacheTimer.time();
        try {
            final StationPackingInstance instance = request.getInstance();
            final String description = instance.hasName() ? instance.getName() : instance.getInfo();
            log.info("Querying the UNSAT cache with coordinate {} for entry {}", request.getCoordinate(), description);
            final ISatisfiabilityCache cache = containmentCacheLocator.locate(request.getCoordinate());
            final ContainmentCacheUNSATResult result = cache.proveUNSATBySubset(instance);
            if (result.isValid()) {
                log.info("Query for UNSAT cache with coordinate {} for entry {} is a hit", request.getCoordinate(), description);
                unsatCacheHits.mark();
            } else {
                log.info("Query for UNSAT cache with coordinate {} for entry {} is a miss", request.getCoordinate(), description);
            }
            return result;
        } finally {
            context.stop();
        }
    }

    @RequestMapping(method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public void cache(
            @RequestBody final ContainmentCacheRequest request
    ) {
        final StationPackingInstance instance = request.getInstance();
        final String description = instance.hasName() ? instance.getName() : instance.getInfo();
        final SolverResult result = request.getResult();
        final ISatisfiabilityCache cache = containmentCacheLocator.locate(request.getCoordinate());

        if (screener.screen(request.getCoordinate(), instance, result)) {
            final String key;
            if (result.getResult().equals(SATResult.SAT)) {
                final ContainmentCacheSATEntry entry = new ContainmentCacheSATEntry(result.getAssignment(), cache.getPermutation());
                key = cacher.cacheSATResult(request.getCoordinate(), entry, instance.hasName() ? instance.getName() : null);
                entry.setKey(key);
                cache.add(entry);
                lastCachedAssignment = request.getResult().getAssignment();
            } else if (result.getResult().equals(SATResult.UNSAT)) {
                final ContainmentCacheUNSATEntry entry = new ContainmentCacheUNSATEntry(instance.getDomains(), cache.getPermutation());
                cache.add(entry);
                key = cacher.cacheUNSATResult(request.getCoordinate(), entry, instance.hasName() ? instance.getName() : null);
                entry.setKey(key);
            } else {
                throw new IllegalStateException("Tried adding a result that was neither SAT or UNSAT");
            }
            log.info("Adding entry to the cache with coordinate {} with key {}. Entry {}", request.getCoordinate(), key, description);
            // add to permanent storage
            cacheAdditions.mark();
        } else {
            log.info("Not adding entry {} to cache {}. No new info", request.getCoordinate(), description);
        }

    }

    @RequestMapping(value = "/filter", method = RequestMethod.POST)
    @ResponseBody
    public void filterCache() {
    	containmentCacheLocator.getCoordinates().forEach(cacheCoordinate -> {
            log.info("Finding SAT entries to be filted at cacheCoordinate {}", cacheCoordinate);
            final ISatisfiabilityCache cache = containmentCacheLocator.locate(cacheCoordinate);
            List<ContainmentCacheSATEntry> SATPrunables = cache.filterSAT();
            log.info("Pruning {} SAT entries from Redis", SATPrunables.size() );
            cacher.deleteSATCollection(SATPrunables);

            log.info("Finding UNSAT entries to be filted at cacheCoordinate {}", cacheCoordinate);
            List<ContainmentCacheUNSATEntry> UNSATPrunables = cache.filterUNSAT();
            log.info("Pruning {} UNSAT entries from Redis", UNSATPrunables.size());
            cacher.deleteUNSATCollection(UNSATPrunables);
        });
        log.info("Filter completed");
    }

    /**
     * Return the last solved SAT problem you know about
     */
    @RequestMapping(value = "/previousAssignment", method = RequestMethod.GET)
    @ResponseBody
    public Map<Integer, Set<Station>> getPreviousAssignment() {
        log.info("Returning the last cached SAT assignment");
        return lastCachedAssignment;
    }


}