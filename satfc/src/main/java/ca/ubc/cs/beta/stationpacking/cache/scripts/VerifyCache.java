package ca.ubc.cs.beta.stationpacking.cache.scripts;

import ca.ubc.cs.beta.aeatk.misc.jcommander.JCommanderHelper;
import ca.ubc.cs.beta.aeatk.misc.options.UsageTextField;
import ca.ubc.cs.beta.aeatk.options.AbstractOptions;
import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.cache.CacheCoordinate;
import ca.ubc.cs.beta.stationpacking.cache.containment.ContainmentCacheSATEntry;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.execution.parameters.SATFCFacadeParameters;
import ca.ubc.cs.beta.stationpacking.facade.SATFCFacade;
import ca.ubc.cs.beta.stationpacking.facade.SATFCFacadeBuilder;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.data.DataManager;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.data.ManagerBundle;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
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
import redis.clients.jedis.Jedis;

import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Created by newmanne on 2016-02-11.
 */
@Slf4j
public class VerifyCache {

    @UsageTextField(title = "Verify script", description = "Parameters needed to verify SAT entries in a cache")
    public static class FilterMandatoryStationsOptions extends AbstractOptions {

        @ParametersDelegate
        @Getter
        private SimpleRedisOptions redisOptions;

        @ParametersDelegate
        @Getter
        private ConstraintFolderOptions constraintFolderOptions;

        @Parameter(names = "-MAX-CHAN", description = "The highest allowable channel")
        @Getter
        private int maxChannel = StationPackingUtils.UHFmax;

        @ParametersDelegate
        @Getter
        private SATFCFacadeParameters facadeParameters;

        @Parameter(names = "-MANDATORY-STATIONS", description = "Stations that must be present in each entry")
        private Set<Integer> stations;

        public Set<Station> getRequiredStations() {
            return stations == null ? Collections.emptySet() : stations.stream().map(Station::new).collect(GuavaCollectors.toImmutableSet());
        }

        @Parameter(names = "-CONSTRAINT-SET", description = "All cache entries will be validated against this constraint set. Absolute path to folder")
        @Getter
        private String masterConstraintFolder;

    }

    @UsageTextField(title="Redis Options",description="Parameters about connecting to redis")
    public static class SimpleRedisOptions extends AbstractOptions {

        @Parameter(names = "-REDIS-PORT", description = "Redis port", required = true)
        private int redisPort;
        @Parameter(names = "-REDIS-HOST", description = "Redis host", required = true)
        private String redisHost ;

        public Jedis getJedis() {
            return new Jedis(redisHost, redisPort, (int) TimeUnit.SECONDS.toMillis(60));
        }

    }

    @UsageTextField(title="Constraint Folder Options",description="Parameters about where to find constraint sets")
    public static class ConstraintFolderOptions extends AbstractOptions {

        @Parameter(names = "--constraint.folder", description = "Folder containing all of the station configuration folders", required = true)
        @Getter
        private String constraintFolder;

    }

    public static class CacheIterator {

        public Iterable<ContainmentCacheSATEntry> iterateSAT() { return null; }

    }

    public static void main(String[] args) throws Exception {
        // Parse args
        final FilterMandatoryStationsOptions options = new FilterMandatoryStationsOptions();
        JCommanderHelper.parseCheckingForHelpAndVersion(args, options);

        Preconditions.checkArgument(options.facadeParameters.cachingParams.serverURL != null, "No server URL specified!");

        // Redis
        final Jedis jedis = options.getRedisOptions().getJedis();

        final Set<Station> requiredStations = options.getRequiredStations();
        final int maxChannel = options.getMaxChannel();
        log.info("Retrying every cache entry that does not contain stations {} and / or has channels in the assignment above {}", requiredStations, maxChannel);

        // Load constraint folders
        final DataManager dataManager = new DataManager();
        dataManager.loadMultipleConstraintSets(options.getConstraintFolderOptions().getConstraintFolder());

        final ManagerBundle managerBundle = dataManager.getData(options.getMasterConstraintFolder());
        final IStationManager stationManager = managerBundle.getStationManager();
        final IConstraintManager constraintManager = managerBundle.getConstraintManager();
        final CacheCoordinate masterCoordinate = managerBundle.getCacheCoordinate();

        // Load up a facade
        @Cleanup
        final SATFCFacade facade = SATFCFacadeBuilder.buildFromParameters(options.facadeParameters);

        final CacheIterator cacheIterator = new CacheIterator(); // TODO:
        for (final ContainmentCacheSATEntry entry : cacheIterator.iterateSAT()) {

            boolean needsSolving = false;
            boolean deleteEntry = false;

            final CacheCoordinate coordinate = CacheCoordinate.fromKey(entry.getKey());
            boolean rightCoordinate = coordinate.equals(masterCoordinate);

            final Set<Station> entryStations = entry.getElements();
            final Set<Station> allRequiredStations = Sets.union(entryStations, requiredStations);

            // Did you just add any new stations? Then we need to resolve the problem
            if (entryStations.size() < allRequiredStations.size()) {
                needsSolving = true;
            }

            // Under this (possibly new) constraint set, is the solution actually valid? Otherwise, it needs resolving
            if (!constraintManager.isSatisfyingAssignment(entry.getAssignmentChannelToStation())) {
                needsSolving = true;
                if (rightCoordinate) {
                    log.warn("Entry for key {} does not have a satisfying assignment (but it should!). Investigate further", entry.getKey());
                }
            }

            // Are some of the values that the stations take in this assignment above the clearing target?
            final Map<Integer, Integer> prevAssignRaw = entry.getAssignmentStationToChannel();
            for (int channel : prevAssignRaw.values()) {
                if (channel > options.getMaxChannel()) {
                    needsSolving = true;
                    break;
                }
            }

            if (needsSolving) {
                deleteEntry = true;
                // Create the domains map
                final Map<Integer, Set<Integer>> domains = new HashMap<>();
                for (Station s: allRequiredStations) {
                    // TODO: this makes no sense and is dumb (e.g. imagine 52)
                    domains.put(s.getID(), stationManager.getRestrictedDomain(s, maxChannel));
                }
                // Make the previous assignment compatible with these domains
                // TODO: the previous assignment might still not be SAT (will this break anything besides making the presolver doomed?)
                final Map<Integer, Integer> previousAssignment = Maps.filterEntries(prevAssignRaw, e -> domains.get(e.getKey()).contains(e.getValue()));
                // This will cause a re-cache of the newly done solution (unless something already exists in the cache)
                facade.solve(domains, previousAssignment, options.facadeParameters.fInstanceParameters.Cutoff, options.facadeParameters.fInstanceParameters.Seed, managerBundle.getInterferenceFolder());
            } else {
                // Make sure the entry has the right coordinate, otherwise rename it
                if (!rightCoordinate) {
                    final long num = Long.parseLong(entry.getKey().substring(entry.getKey().lastIndexOf(':') + 1));
                    final String newKey = masterCoordinate.toKey(SATResult.SAT, num);
                    jedis.rename(entry.getKey(), newKey);
                }
            }
            if (deleteEntry) {
                log.debug("Deleting entry {}", entry.getKey());
                jedis.del(entry.getKey());
            }
        }
    }

}