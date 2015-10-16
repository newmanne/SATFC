package ca.ubc.cs.beta.stationpacking.facade;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.DomainStationManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.execution.extendedcache.CSVStationDB;
import ca.ubc.cs.beta.stationpacking.execution.extendedcache.IStationDB;
import ca.ubc.cs.beta.stationpacking.execution.extendedcache.IStationSampler;
import ca.ubc.cs.beta.stationpacking.execution.extendedcache.PopulationVolumeSampler;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.cache.ContainmentCacheProxy;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;
import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.type.MapType;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Maps;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.util.EntityUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Created by newmanne on 15/10/15.
 */
@Slf4j
public class SATFCCacheAugmenter {

    private final SATFCFacade facade;
    private volatile InterruptibleSATFCResult currentResult;
    private final AtomicBoolean isAugmenting;

    public SATFCCacheAugmenter(SATFCFacade facade) {
        this.facade = facade;
        isAugmenting = new AtomicBoolean(false);
    }

    /**
     * @param stationConfigFolder a station configuration folder also containing a station info and augment domain file
     * @param cutoff cutoff to use for augmenting
     */
    public void augment(String stationConfigFolder, String serverURL, double cutoff) {
        final String stationInfoFile = stationConfigFolder + File.separator + "Station_Info.csv";
        Preconditions.checkState(new File(stationInfoFile).exists(), "Station info file %s does not exist", stationInfoFile);
        final CSVStationDB csvStationDB = new CSVStationDB(stationInfoFile);

        // load up domains...
        final String cacheDomainFile = stationConfigFolder + File.separator + "Augment_Domains.csv";
        Preconditions.checkState(new File(cacheDomainFile).exists(), "Augment domain file %s does not exist", cacheDomainFile);
        log.info("Parsing domains from {}", cacheDomainFile);
        final IStationManager manager;
        try {
            manager = new DomainStationManager(cacheDomainFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Couldn't load domains for augmentation", e);
        }

        // get a previous assignment from the cache
        log.info("Asking cache for last seen SAT assignment");
        final Map<Integer, Integer> previousAssignment = getPreviousAssignmentFromCache(serverURL);

        // now some of the channels from this previous assignment might actually be off domain. If they are, let's assume impairment and fix them
        final Map<Integer, Set<Integer>> domains = new HashMap<>();
        manager.getStations().forEach(station -> {
            domains.put(station.getID(), manager.getDomain(station));
            Integer prevChannel = previousAssignment.get(station.getID());
            if (prevChannel != null && !manager.getDomain(station).contains(prevChannel)) {
                log.debug("Station {} is off of its domain, considered impairing and fixing it to {}", station, prevChannel);
                domains.put(station.getID(), Collections.singleton(prevChannel));
            }
        });

        log.info("Going into main augment method");
        augment(domains, previousAssignment, csvStationDB, stationConfigFolder, cutoff);
    }

    private Map<Integer, Integer> getPreviousAssignmentFromCache(String serverURL) {
        final UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(serverURL + "/v1/cache/previousAssignment");
        final String uriString = builder.build().toUriString();
        final HttpGet httpPost = new HttpGet(uriString);
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<HttpResponse> serverResponse = new AtomicReference<>();
        final AtomicReference<Exception> exception = new AtomicReference<>();
        ContainmentCacheProxy.getClient().execute(httpPost, new FutureCallback<HttpResponse>() {
            @Override
            public void completed(HttpResponse result) {
                log.info("Got back answer from server");
                serverResponse.set(result);
                latch.countDown();
            }

            @Override
            public void failed(Exception ex) {
                exception.set(ex);
                latch.countDown();
            }

            @Override
            public void cancelled() {
                latch.countDown();
            }
        });
        log.info("Going to sleep");
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        final String response;
        final Map<Integer, Set<Station>> assignment;
        try {
            response = EntityUtils.toString(serverResponse.get().getEntity());
            assignment = JSONUtils.getMapper().readValue(response, new TypeReference<Map<Integer, Set<Station>>>() {});
        } catch (IOException e) {
            throw new RuntimeException("Can't parse server result", e);
        }
        final Exception ex = exception.get();
        if (ex != null) {
            throw new RuntimeException("Error making web request", ex);
        }
        log.info("Back from server");
        return StationPackingUtils.stationToChannelFromChannelToStation(assignment).entrySet().stream().collect(Collectors.toMap(entry -> entry.getKey().getID(), Map.Entry::getValue));
    }

    /**
     * @param domains             a map taking integer station IDs to set of integer channels domains.
     * @param previousAssignment  a valid (proved to not create any interference) partial (can concern only some of the provided station) station to channel assignment.
     * @param stationConfigFolder a folder in which to find station config data (<i>i.e.</i> interferences and domains files).
     * @param cutoff
     */
    public void augment(@NonNull Map<Integer, Set<Integer>> domains,
                        @NonNull Map<Integer, Integer> previousAssignment,
                        @NonNull IStationDB stationDB,
                        @NonNull String stationConfigFolder,
                        double cutoff
    ) {
        isAugmenting.set(true);
        log.info("Augmenting the following stations {}", previousAssignment.keySet());
        // These stations will be in every single problem
        final Set<Integer> exitedStations = previousAssignment.keySet();

        // Init the station sampler
        final IStationSampler sampler = new PopulationVolumeSampler(stationDB, domains.keySet(), RandomUtils.nextInt(0, Integer.MAX_VALUE));

        while (isAugmenting.get()) {
            log.debug("Starting to augment from the initial state");
            // The set of stations that we are going to pack in every problem
            final Set<Integer> packingStations = new HashSet<>(exitedStations);
            Map<Integer, Integer> currentAssignment = new HashMap<>(previousAssignment);

            // Augment
            while (isAugmenting.get()) {
                // Sample a new station
                final Integer sampledStationId = sampler.sample(packingStations);
                log.info("Trying to augment station {}", sampledStationId);
                packingStations.add(sampledStationId);
                final Map<Integer, Set<Integer>> reducedDomains = Maps.filterEntries(domains, new Predicate<Map.Entry<Integer, Set<Integer>>>() {
                    @Override
                    public boolean apply(Map.Entry<Integer, Set<Integer>> input) {
                        return packingStations.contains(input.getKey());
                    }
                });
                // Solve!
                currentResult = facade.createInterruptibleSATFCResult(reducedDomains, currentAssignment, cutoff, RandomUtils.nextInt(1, Integer.MAX_VALUE), stationConfigFolder, "Augment", true);
                final SATFCResult result = currentResult.computeResult();
                log.debug("Result is {}", result);
                if (result.getResult().equals(SATResult.SAT)) {
                    // Result was SAT. Let's continue down this trajectory
                    log.info("Result was SAT. Adding station {} to trajectory", sampledStationId);
                    currentAssignment = result.getWitnessAssignment();
                } else {
                    log.info("Non-SAT result reached. Restarting from initial state");
                    // Either UNSAT or TIMEOUT. Time to restart
                    break;
                }
            }
        }

    }

    /**
     * Stop cache augmentation
     */
    public void stop() {
        isAugmenting.set(false);
        final InterruptibleSATFCResult result = currentResult;
        if (result != null) {
            result.interrupt();
            try {
                result.blockUntilTerminated();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
