package ca.ubc.cs.beta.stationpacking.execution;

import ca.ubc.cs.beta.aeatk.misc.jcommander.JCommanderHelper;
import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.cache.ICacher;
import ca.ubc.cs.beta.stationpacking.cache.containment.ContainmentCacheSATEntry;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.DomainStationManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.execution.extendedcache.IProblemSampler;
import ca.ubc.cs.beta.stationpacking.execution.extendedcache.IStationSampler;
import ca.ubc.cs.beta.stationpacking.execution.extendedcache.ProblemSamplerFactory;
import ca.ubc.cs.beta.stationpacking.execution.extendedcache.StationSamplerFactory;
import ca.ubc.cs.beta.stationpacking.execution.parameters.ExtendedCacheProblemProducerParameters;
import ca.ubc.cs.beta.stationpacking.execution.problemgenerators.SATFCFacadeProblem;
import ca.ubc.cs.beta.stationpacking.utils.GuavaCollectors;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;
import ca.ubc.cs.beta.stationpacking.utils.RedisUtils;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Created by emily404 on 5/28/15.
 */
@Slf4j
public class ExtendedCacheProblemProducer {

    private static final long THREAD_SLEEP_MILLIS = 5 * 10^5;
    private static final long QUEUE_SIZE_THRESHOLD = 100;

    /**
     * This method takes an existing SAT cache entry and generate a new problem by adding a new station
     * The problem identifier is push into jobQueue and the problem json is stored at problemHash
     * @throws FileNotFoundException
     */
    public static void main(String[] args) throws FileNotFoundException, InterruptedException {

        ExtendedCacheProblemProducerParameters parameters = new ExtendedCacheProblemProducerParameters();
        JCommanderHelper.parseCheckingForHelpAndVersion(args, parameters);
        parameters.fLoggingOptions.initializeLogging();

        String domainCSV = parameters.fInterferencesFolder + "/Domain.csv";
        IStationManager stationManager = new DomainStationManager(domainCSV);

        Jedis jedis = parameters.fRedisParameters.getJedis();
        IStationSampler stationSampler = StationSamplerFactory.getStationSampler(parameters.fStationSampler);
        IProblemSampler problemSampler = ProblemSamplerFactory.getProblemSampler(parameters.fProblemSampler);

        // fresh start, clear all queues if restarted upon crash
        Set<String> queueKeys = jedis.keys("*" + parameters.fRedisParameters.fRedisQueue + "*");
        queueKeys.forEach(key -> jedis.del(key));

        // populating keyQueue simple version
        Set<String> satKeys = jedis.keys("*:SAT*");
        satKeys.forEach(key -> jedis.lpush(parameters.fRedisParameters.fRedisQueue, key));

        while(true){
            if(queueSizeLow(jedis, parameters.fRedisParameters.fRedisQueue)){
                problemSampler.sample();
                if(queueSizeLow(jedis, parameters.fRedisParameters.fRedisQueue)) {
                    Thread.sleep(THREAD_SLEEP_MILLIS);
                }
            } else {
                String key;
                while((key = jedis.lpop(parameters.fRedisParameters.fRedisQueue)) != null){
                    log.debug("key:" + key);
                    String entry = jedis.get(key);
                    log.debug("entry:" + entry);
                    //build problem instance
                    ICacher.SATCacheEntry cacheEntry = JSONUtils.toObject(entry, ICacher.SATCacheEntry.class);
                    final ContainmentCacheSATEntry ccEntry = new ContainmentCacheSATEntry(cacheEntry.getAssignment(), key);

                    Set<Integer> stations = new HashSet<>();
                    StreamSupport.stream(ccEntry.getElements().spliterator(), false)
                            .forEach(station -> stations.add(station.getID()));

                    Map<Integer, Set<Integer>> domains = new HashMap<>();
                    StreamSupport.stream(ccEntry.getElements().spliterator(), false)
                            .forEach(station -> {
                                // TODO: filter out the special channel 37?
                                Set<Integer> clearedDomain = StreamSupport.stream(stationManager.getDomain(station).spliterator(), false)
                                        .filter(channel -> channel <= parameters.fClearingTarget)
                                        .collect(Collectors.toSet());
                                domains.put(station.getID(), clearedDomain);
                            });

                    // TODO: use stationSampler to get the next station to add
                    //add a new station
                    BitSet flipped = (BitSet)ccEntry.getBitSet().clone();
                    flipped.flip(1, flipped.length());
                    Set<Integer> stationsToAdd = flipped.stream().mapToObj(Integer::new).collect(GuavaCollectors.toImmutableSet());
                    StreamSupport.stream(stationsToAdd.spliterator(), false)
                            .forEach(station -> {
                                stations.add(station);
                                Set<Integer> clearedDomain = StreamSupport.stream(stationManager.getDomain(new Station(station)).spliterator(), false)
                                        .filter(channel -> channel <= parameters.fClearingTarget)
                                        .collect(Collectors.toSet());
                                domains.put(station, clearedDomain);

                                Set<Integer> channelsToPackOn = domains.values().stream().reduce(new HashSet<>(), Sets::union);
                                String instanceName = getHashString(domains.toString()+ccEntry.toString());
                                SATFCFacadeProblem problem =
                                        new SATFCFacadeProblem(stations, channelsToPackOn, domains, ccEntry.getAssignment(), parameters.fInterferencesFolder, instanceName);

                                //load key into job queue and load problem json into hash atomically
                                Transaction multi = jedis.multi();
                                multi.rpush(RedisUtils.makeKey(parameters.fRedisParameters.fRedisQueue, RedisUtils.JOB_QUEUE), instanceName);
                                String json = JSONUtils.toString(problem);
                                multi.hset(RedisUtils.makeKey(parameters.fRedisParameters.fRedisQueue, RedisUtils.JSON_HASH), instanceName, json);
                                multi.exec();

                                //restore stations list and domain list after building each problem
                                stations.remove(station);
                                domains.remove(station);
                            });
                }
            }
        }

    }

    private static String getHashString(String aString)
    {
        MessageDigest aDigest = DigestUtils.getSha1Digest();
        try {
            byte[] aResult = aDigest.digest(aString.getBytes("UTF-8"));
            String aResultString = new String(Hex.encodeHex(aResult));
            return "generated:" + aResultString;
        }
        catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("Could not encode filename", e);
        }
    }

    private static boolean queueSizeLow(Jedis jedis, String queue){
        if(jedis.llen(queue) < QUEUE_SIZE_THRESHOLD){
            return true;
        }
        return false;
    }

}
