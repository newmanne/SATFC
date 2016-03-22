package ca.ubc.cs.beta.stationpacking.cache.scripts;

import ca.ubc.cs.beta.aeatk.misc.jcommander.JCommanderHelper;
import ca.ubc.cs.beta.aeatk.misc.options.UsageTextField;
import ca.ubc.cs.beta.aeatk.options.AbstractOptions;
import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.cache.CacheCoordinate;
import ca.ubc.cs.beta.stationpacking.cache.RedisCacher;
import ca.ubc.cs.beta.stationpacking.cache.containment.ContainmentCacheSATEntry;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.execution.parameters.SATFCFacadeParameters;
import ca.ubc.cs.beta.stationpacking.facade.SATFCFacade;
import ca.ubc.cs.beta.stationpacking.facade.SATFCFacadeBuilder;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.data.DataManager;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.data.ManagerBundle;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.utils.CacheUtils;
import ca.ubc.cs.beta.stationpacking.utils.GuavaCollectors;
import ca.ubc.cs.beta.stationpacking.utils.RedisUtils;
import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParametersDelegate;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.Cleanup;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by newmanne on 2016-02-11.
 */
@Slf4j
public class ConstrainCache {

    @UsageTextField(title = "Verify script", description = "Parameters needed to verify SAT entries in a cache")
    public static class FilterMandatoryStationsOptions extends AbstractOptions {

        @Parameter(names = "-MAX-CHANNEL", description = "The clearing target used for any cache entries that trigger resolving", required = true)
        @Getter
        private int maxChannel;

        @ParametersDelegate
        @Getter
        private SATFCFacadeParameters facadeParameters = new SATFCFacadeParameters();

        @Parameter(names = "-MANDATORY-STATIONS", description = "Stations that must be present in each entry")
        private List<Integer> stations;

        public Set<Station> getRequiredStations() {
            return stations == null ? Collections.emptySet() : stations.stream().map(Station::new).collect(GuavaCollectors.toImmutableSet());
        }

        @Parameter(names = "-CONSTRAINT-SET", description = "All cache entries will be validated against this constraint set. Absolute path to folder")
        @Getter
        private String masterConstraintFolder;

        @Parameter(names = "-ALL-CONSTRAINTS", description = "Folder with all constraint sets", required = true)
        @Getter
        private String allConstraints;

        @Getter
        @Parameter(names = "-ENFORCE-MAX-CHANNEL", description = "Force a resolve (and delete) any cache entry where a station is assigned above the max channel")
        private boolean enforceMaxChannel = false;

        @Getter
        @Parameter(names = "-DISTRIBUTED", description = "Use the redis queuing system")
        private boolean distributed = false;


    }

    public static void main(String[] args) throws Exception {
        // Parse args
        final FilterMandatoryStationsOptions options = new FilterMandatoryStationsOptions();
        JCommanderHelper.parseCheckingForHelpAndVersion(args, options);

        if (options.isDistributed()) {
            Preconditions.checkArgument(options.facadeParameters.cachingParams.serverURL != null, "No server URL specified!");
        } else if (options.facadeParameters.cachingParams.serverURL != null) {
            log.warn("No server URL specified. This script likely won't have side effects...");
        }


        final Jedis jedis = options.getFacadeParameters().fRedisParameters.getJedis();

        final Set<Station> requiredStations = options.getRequiredStations();
        log.info("Retrying every cache entry that does not contain stations {}", requiredStations);

        // Load constraint folders
        final DataManager dataManager = new DataManager();
        dataManager.loadMultipleConstraintSets(options.getAllConstraints());
        final ManagerBundle managerBundle = dataManager.getData(options.getMasterConstraintFolder());
        final IStationManager stationManager = managerBundle.getStationManager();
        final IConstraintManager constraintManager = managerBundle.getConstraintManager();
        final CacheCoordinate masterCoordinate = managerBundle.getCacheCoordinate();

        log.info("Migrating all cache entries to match constraints in {} ({})", options.getMasterConstraintFolder(), masterCoordinate);

        // Load up a facade
        @Cleanup
        final SATFCFacade facade = SATFCFacadeBuilder.buildFromParameters(options.facadeParameters);

        final RedisCacher redisCacher = new RedisCacher(dataManager, options.getFacadeParameters().fRedisParameters.getStringRedisTemplate(), options.getFacadeParameters().fRedisParameters.getBinaryJedis());

        if (!options.isDistributed()) {
            for (ContainmentCacheSATEntry containmentCacheSATEntry : redisCacher.iterateSAT()) {
                doThing(containmentCacheSATEntry, masterCoordinate, requiredStations, constraintManager, stationManager, options, facade, managerBundle, jedis);
            }
        } else {
            String key;
            String queueName = options.getFacadeParameters().fRedisParameters.fRedisQueue;
            while (true) {
                key = jedis.rpoplpush(RedisUtils.makeKey(queueName), RedisUtils.makeKey(queueName, RedisUtils.PROCESSING_QUEUE));
                if (key == null) {
                    break;
                }
                final CacheUtils.ParsedKey parsedKey;
                try {
                    parsedKey = CacheUtils.parseKey(key);
                } catch (Exception e) {
                    continue;
                }
                if (parsedKey.getResult().equals(SATResult.SAT)) {
                    ContainmentCacheSATEntry entry = (ContainmentCacheSATEntry) redisCacher.cacheEntryFromKey(key);
                    doThing(entry, masterCoordinate, requiredStations, constraintManager, stationManager, options, facade, managerBundle, jedis);
                }
            }
        }
        log.info("Finished. You should now restart the SATFCServer");
    }


    public static void doThing(ContainmentCacheSATEntry entry, CacheCoordinate masterCoordinate, Set<? extends Station> requiredStations, IConstraintManager constraintManager, IStationManager stationManager, FilterMandatoryStationsOptions options, SATFCFacade facade, ManagerBundle managerBundle, Jedis jedis) {
        final int maxChannel = options.getMaxChannel();

        log.info("Entry {} stats: Domain size is {}", entry.getKey(), entry.getElements().size());

        final CacheCoordinate coordinate = CacheCoordinate.fromKey(entry.getKey());

        // Just verify, and get out of here
        boolean rightCoordinate = coordinate.equals(masterCoordinate);
        if (rightCoordinate) {
            log.debug("The cache entry is already in the right coordinate. Just verifying.");
            if (constraintManager.isSatisfyingAssignment(entry.getAssignmentChannelToStation())) {
                log.debug("Assignment is valid");
                return;
            } else {
                throw new IllegalStateException("ENTRY " + entry.getKey() + " VIOLATES CONSTRAINTS!!!");
            }
        }

        // Some stations may have been deleted in between constraint sets, so let's get rid of them
        final Set<Station> entryStations = Sets.intersection(entry.getElements(), stationManager.getStations());
        final Set<Station> allRequiredStations = Sets.union(entryStations, requiredStations);

        final Map<Integer, Integer> previousAssignment;
        final Map<Integer, Set<Integer>> domains;

        // A) I can transfer b/c once I've removed stations that no longer exist, I'm still valid
        final Map<Integer, Integer> entryAssignment = Maps.filterKeys(entry.getAssignmentStationToChannel(), s -> entryStations.contains(new Station(s)));
        if (constraintManager.isSatisfyingAssignment(StationPackingUtils.channelToStationFromStationToChannel(entryAssignment))) {
            previousAssignment = entryAssignment;
            domains = previousAssignment.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> Collections.singleton(e.getValue())));
        } else {
            // B) I have to be resolved.
            domains = new HashMap<>();
            for (Station s : allRequiredStations) {
                Integer prevChan = entryAssignment.get(s.getID());
                if (prevChan != null && prevChan > maxChannel) {
                    log.debug("Station {} above max chan (probably impairing?)", s);
                    domains.put(s.getID(), Collections.singleton(prevChan));
                } else {
                    Set<Integer> restrictedDomain;
                    try {
                        restrictedDomain = stationManager.getRestrictedDomain(s, maxChannel, true);
                        if (!restrictedDomain.isEmpty()) {
                            domains.put(s.getID(), restrictedDomain);
                        } else {
                            log.debug("Skipping station {} due to no domain", s);
                        }
                    } catch (Exception e) {
                        log.debug("Skipping station {} due to no domain", s);
                    }
                }
            }
            // Make the previous assignment compatible with these domains

            previousAssignment = Maps.filterEntries(entry.getAssignmentStationToChannel(), e -> domains.get(e.getKey()) != null && domains.get(e.getKey()).contains(e.getValue()));

            log.debug("Pre prev assign size is {}", previousAssignment.size());

            // Make the previous assignment at least consistent in a stupid greedy way
            List<Integer> stations = new ArrayList<>(previousAssignment.keySet());
            for (Integer s1 : stations) {
                if (previousAssignment.containsKey(s1)) {
                    for (Integer s2 : stations) {
                        if (previousAssignment.containsKey(s2) && !Objects.equals(s1, s2)) {
                            if (!constraintManager.isSatisfyingAssignment(new Station(s1), previousAssignment.get(s1), new Station(s2), previousAssignment.get(s2))) {
                                // Violation! Remove s1 arbitrarily and continue
                                previousAssignment.remove(s1);
                                break;
                            }
                        }
                    }
                }
            }

        }

        if (!domains.values().stream().anyMatch(Set::isEmpty)) {
            // This will cause a re-cache of the newly done solution (unless something already exists in the cache)
            log.trace("Domains are {}", domains);
            log.info("Solving problem domain size is {}, prev assign size is {}", domains.size(), previousAssignment.size());
            final SATFCResult solve = facade.solve(domains, previousAssignment, options.facadeParameters.fInstanceParameters.Cutoff, options.facadeParameters.fInstanceParameters.Seed, managerBundle.getInterferenceFolder());
            if (solve.getResult().equals(SATResult.SAT)) {
                log.info("Resolve successful");
            } else {
                log.info("Resolve failed, {}", solve.getResult());
            }
        } else {
            log.info("Skipping resolve due to empty domain");
        }

        // Are some of the values that the stations take in this assignment above the clearing target?
//        if (options.isEnforceMaxChannel()) {
//            for (int channel : prevAssignRaw.values()) {
//                if (channel > options.getMaxChannel()) {
//                    log.debug("Entry {} has a station assigned to a channel {} higher than {}... requires resolve", entry.getKey(), channel, options.getMaxChannel());
//                    needsSolving = true;
//                }
//            }
//        }


    }

}