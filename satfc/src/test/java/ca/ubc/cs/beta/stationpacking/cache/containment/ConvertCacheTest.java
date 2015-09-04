package ca.ubc.cs.beta.stationpacking.cache.containment;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import org.junit.Ignore;
import org.junit.Test;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisShardInfo;
import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.cache.ICacher;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.ChannelSpecificConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.DomainStationManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

/**
 * Created by newmanne on 24/04/15.
 */
@Slf4j
public class ConvertCacheTest {

    @JsonDeserialize(using = CacheEntry.CacheEntryDeserializer.class)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Data
    public static  class CacheEntry {

        private final SolverResult solverResult;
        private final Map<Station, Set<Integer>> domains;
        private final Date cacheDate;
        private final String name;

        public static class CacheEntryDeserializer extends JsonDeserializer<CacheEntry> {

            @Override
            public CacheEntry deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
                final CacheEntryJson cacheEntryJson = jp.readValueAs(CacheEntryJson.class);
                final Map<Station, Set<Integer>> collect = cacheEntryJson.getDomains().entrySet()
                        .stream()
                        .collect(Collectors.toMap(e -> new Station(e.getKey()), Map.Entry::getValue));
                return new CacheEntry(cacheEntryJson.getSolverResult(), collect, cacheEntryJson.getCacheDate(), cacheEntryJson.getName());
            }

        }

        @Data
        private static class CacheEntryJson {
            private SolverResult solverResult;
            private Map<Integer, Set<Integer>> domains;
            private Date cacheDate;
            private String name;
        }

    }

    @Test
    @Ignore
    public void encrypt() throws Exception {
        // Load up the encryption csv
        final String encryptionCSV = "/ubc/cs/research/arrow/satfc/instances/encodings/encoding_ADJ2.csv";
        final File encryptionFile = new File(encryptionCSV);
        final Map<Integer, Integer> oldToNew = new HashMap<>();
        Files.readLines(encryptionFile, Charset.defaultCharset()).stream().skip(1).forEach(line -> {
            final List<String> strings = Splitter.on(',').splitToList(line);
            oldToNew.put(Integer.parseInt(strings.get(0)), Integer.parseInt(strings.get(1)));
        });

        final String interferencePath = "/ubc/cs/research/arrow/satfc/public/interference/052815SC32M3";
        final IStationManager stationManager = new DomainStationManager(interferencePath + "/Domain.csv");
        final IConstraintManager manager = new ChannelSpecificConstraintManager(stationManager, interferencePath + "/Interference_Paired.csv");
        final String domainHash = stationManager.getDomainHash();
        final String interferenceHash = manager.getConstraintHash();

        final Set<String> SATKeys = new HashSet<>();
        final Set<String> UNSATKeys = new HashSet<>();

        // Assemble all the old keys
        final StringRedisTemplate fromRedisTemplate = new StringRedisTemplate(new JedisConnectionFactory(new JedisShardInfo("localhost", 7777)));

        final Cursor<byte[]> scan = fromRedisTemplate.getConnectionFactory().getConnection().scan(ScanOptions.scanOptions().build());
        while (scan.hasNext()) {
            final String key = new String(scan.next());
            if (key.startsWith("SATFC:SAT:31edfbf3:65f1b0f5")) {
                SATKeys.add(key);
            } else if (key.startsWith("SATFC:UNSAT:31edfbf3:65f1b0f5")) {
                UNSATKeys.add(key);
            }
        }

        log.info("Found " + SATKeys.size() + " SAT keys");
        log.info("Found " + UNSATKeys.size() + " UNSAT keys");

        // process SATs
        final AtomicInteger progressIndex = new AtomicInteger();
        SATKeys.forEach(key -> {
            if (progressIndex.get() % 1000 == 0) {
                log.info("Processed " + progressIndex.get() + " SAT keys out of " + SATKeys.size());
            }
            final String val = fromRedisTemplate.boundValueOps(key).get();
            final ICacher.SATCacheEntry satCacheEntry = JSONUtils.toObject(val, ICacher.SATCacheEntry.class);
            // convert it!
            Map<Integer, Set<Station>> newAssignment = new HashMap<>();
            satCacheEntry.getAssignment().entrySet().stream().forEach(entry -> newAssignment.put(entry.getKey(), entry.getValue().stream().map(s -> new Station(oldToNew.get(s.getID()))).collect(Collectors.toSet())));
            final ICacher.SATCacheEntry satCacheEntry1 = new ICacher.SATCacheEntry(satCacheEntry.getMetadata(), newAssignment);
            final String jsonResult = JSONUtils.toString(satCacheEntry1);
            final String instanceHash = Iterables.getLast(Splitter.on(":").split(key));
            final String newKey = Joiner.on(":").join(ImmutableList.of("SATFC", SATResult.SAT, domainHash, interferenceHash, instanceHash));
            fromRedisTemplate.boundValueOps(newKey).set(jsonResult);
            fromRedisTemplate.delete(key);
            progressIndex.incrementAndGet();
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        log.info("Finished processing {} SAT entries", progressIndex.get());

        progressIndex.set(0);
        UNSATKeys.forEach(key -> {
            if (progressIndex.get() % 1000 == 0) {
                log.info("Processed " + progressIndex.get() + " UNSAT keys out of " + UNSATKeys.size());
            }
            final String val = fromRedisTemplate.boundValueOps(key).get();
            final ICacher.UNSATCacheEntry cacheEntry = JSONUtils.toObject(val, ICacher.UNSATCacheEntry.class);
            final Map<Station, Set<Integer>> newDomains = new HashMap<>();
            cacheEntry.getDomains().entrySet().stream().forEach(entry -> newDomains.put(new Station(oldToNew.get(entry.getKey().getID())), entry.getValue()));
            final ICacher.UNSATCacheEntry newEntry = new ICacher.UNSATCacheEntry(cacheEntry.getMetadata(), newDomains);
            final String jsonResult = JSONUtils.toString(newEntry);
            final String instanceHash = Iterables.getLast(Splitter.on(":").split(key));
            final String newKey = Joiner.on(":").join(ImmutableList.of("SATFC", SATResult.UNSAT, domainHash, interferenceHash, instanceHash));
            fromRedisTemplate.boundValueOps(newKey).set(jsonResult);
            fromRedisTemplate.delete(key);
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            progressIndex.incrementAndGet();
        });
        log.info("Finished processing {} UNSAT entries", progressIndex.get());
    }

}