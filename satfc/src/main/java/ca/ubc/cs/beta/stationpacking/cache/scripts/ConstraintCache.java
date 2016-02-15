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
import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParametersDelegate;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.Cleanup;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import redis.clients.jedis.BinaryJedis;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisShardInfo;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Created by newmanne on 2016-02-11.
 */
@Slf4j
public class ConstraintCache {

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

        @Parameter(names = "-ALL-CONSTRAINTS", description = "Folder with all constraint sets", required=true)
        @Getter
        private String allConstraints;

        @Getter
        @Parameter(names = "-ENFORCE-MAX-CHANNEL", description = "Force a resolve (and delete) any cache entry where a station is assigned above the max channel")
        private boolean enforceMaxChannel = false;

    }

    public static void main(String[] args) throws Exception {
        // Parse args
        final FilterMandatoryStationsOptions options = new FilterMandatoryStationsOptions();
        JCommanderHelper.parseCheckingForHelpAndVersion(args, options);

        Preconditions.checkArgument(options.facadeParameters.cachingParams.serverURL != null, "No server URL specified!");

        final Jedis jedis = options.getFacadeParameters().fRedisParameters.getJedis();

        final Set<Station> requiredStations = options.getRequiredStations();
        final int maxChannel = options.getMaxChannel();
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

        for (final ContainmentCacheSATEntry entry : redisCacher.iterateSAT()) {
            log.debug("Examining entry {}", entry.getKey());
            boolean needsSolving = false;

            final CacheCoordinate coordinate = CacheCoordinate.fromKey(entry.getKey());
            boolean rightCoordinate = coordinate.equals(masterCoordinate);

            final Set<Station> entryStations = entry.getElements();
            final Set<Station> allRequiredStations = Sets.union(entryStations, requiredStations);

            // Did you just add any new stations? Then we need to resolve the problem
            if (entryStations.size() < allRequiredStations.size()) {
                log.info("Entry {} is missing stations... requires resolve", entry.getKey());
                needsSolving = true;
            }

            // Under this (possibly new) constraint set, is the solution actually valid? Otherwise, it needs resolving
            if (!constraintManager.isSatisfyingAssignment(entry.getAssignmentChannelToStation())) {
                log.info("Entry {} violates constraints... needs resolve", entry.getKey());
                needsSolving = true;
                if (rightCoordinate) {
                    log.warn("Entry for key {} does not have a satisfying assignment (but it should!). Investigate further", entry.getKey());
                }
            }

            // Are some of the values that the stations take in this assignment above the clearing target?
            if (options.isEnforceMaxChannel()) {
                final Map<Integer, Integer> prevAssignRaw = entry.getAssignmentStationToChannel();
                for (int channel : prevAssignRaw.values()) {
                    if (channel > options.getMaxChannel()) {
                        log.info("Entry {} has a station assigned to a channel higher than {}... requires resolve", entry.getKey(), options.getMaxChannel());
                        needsSolving = true;
                        break;
                    }
                }
            }


            if (needsSolving) {
                // Create the domains map
                final Map<Integer, Set<Integer>> domains = new HashMap<>();
                for (Station s: allRequiredStations) {
                    Set<Integer> restrictedDomain;
                    try {
                        restrictedDomain = stationManager.getRestrictedDomain(s, maxChannel);
                    } catch (Exception e) {
                        restrictedDomain = Collections.emptySet();
                    }
                    domains.put(s.getID(), restrictedDomain);
                }
                // Make the previous assignment compatible with these domains
                final Map<Integer, Integer> previousAssignment = Maps.filterEntries(entry.getAssignmentStationToChannel(), e -> domains.get(e.getKey()).contains(e.getValue()));

                if (!domains.values().stream().anyMatch(Set::isEmpty)) {
                    // This will cause a re-cache of the newly done solution (unless something already exists in the cache)
                    final SATFCResult solve = facade.solve(domains, previousAssignment, options.facadeParameters.fInstanceParameters.Cutoff, options.facadeParameters.fInstanceParameters.Seed, managerBundle.getInterferenceFolder());
                    if (solve.getResult().equals(SATResult.SAT)) {
                        log.info("Resolve successful");
                    } else {
                        log.info("Resolve failed, {}", solve.getResult());
                    }
                } else {
                    log.info("Skipping resolve due to empty domain");
                }
                log.info("Deleting entry {}", entry.getKey());
                jedis.del(entry.getKey());
            } else {
                // Make sure the entry has the right coordinate, otherwise rename it
                if (!rightCoordinate) {
                    final long num = CacheUtils.parseKey(entry.getKey()).getNum() + 1;
                    final String newKey = masterCoordinate.toKey(SATResult.SAT, num);
                    jedis.rename(entry.getKey(), newKey);
                }
            }
        }
        log.info("Finished. You should now restart the SATFCServer");
    }

}