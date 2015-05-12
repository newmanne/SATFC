package ca.ubc.cs.beta.stationpacking.execution.problemgenerators;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import ca.ubc.cs.beta.stationpacking.execution.AProblemGenerator;
import ca.ubc.cs.beta.stationpacking.execution.Converter;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
import ca.ubc.cs.beta.stationpacking.metrics.SATFCMetrics;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;
import ca.ubc.cs.beta.stationpacking.utils.RedisUtils;

import com.google.common.collect.Sets;

/**
* Created by newmanne on 12/05/15.
*/
@Slf4j
public class RedisProblemGenerator extends AProblemGenerator {

    private final Jedis jedis;
    private final String queueName;
    private final String interferencesFolder;
    private String activeProblemFullPath;

    public RedisProblemGenerator(String host, int port, String queueName, String interferencesFolder) {
        this.interferencesFolder = interferencesFolder;
        jedis = new Jedis(host, port);
        if (!jedis.exists(RedisUtils.makeKey(queueName))) {
            throw new IllegalArgumentException("Queue " + queueName + " does not exist");
        }
        this.queueName = queueName;
        log.info("Reading instances from {}:{} on queue {}", host, port, RedisUtils.makeKey());
        SATFCMetrics.init();
    }

    @Override
    public SATFCFacadeProblem getNextProblem() {
        Converter.StationPackingProblemSpecs stationPackingProblemSpecs;
        String fullPathToInstanceFile;
        String instanceFileName;
        while (true) {
            fullPathToInstanceFile = jedis.rpoplpush(queueName, RedisUtils.makeKey(queueName, RedisUtils.PROCESSING_QUEUE));
            if (fullPathToInstanceFile == null) { // all problems exhausted
                return null;
            }
            instanceFileName = new File(fullPathToInstanceFile).getName();
            final long remainingJobs = jedis.llen(queueName);
            log.info("Beginning problem {}; this is my {}th problem; there are {} problems remaining in the queue", instanceFileName, index, remainingJobs);
            try {
                stationPackingProblemSpecs = Converter.StationPackingProblemSpecs.fromStationRepackingInstance(fullPathToInstanceFile);
                break;
            } catch (IOException e) {
                log.warn("Error parsing file " + fullPathToInstanceFile, e);
            }
        }
        activeProblemFullPath = fullPathToInstanceFile;
        final Set<Integer> stations = stationPackingProblemSpecs.getDomains().keySet();
        SATFCMetrics.postEvent(new SATFCMetrics.NewStationPackingInstanceEvent(stations, instanceFileName));
        return new SATFCFacadeProblem(
                stations,
                stationPackingProblemSpecs.getDomains().values().stream().reduce(new HashSet<>(), Sets::union),
                stationPackingProblemSpecs.getDomains(),
                stationPackingProblemSpecs.getPreviousAssignment(),
                interferencesFolder + File.separator + stationPackingProblemSpecs.getDataFoldername(),
                instanceFileName
        );
    }

    @Override
    public void onPostProblem(SATFCResult result) {
        super.onPostProblem(result);
        final String instanceFileName = new File(activeProblemFullPath).getName();
        SATFCMetrics.postEvent(new SATFCMetrics.InstanceSolvedEvent(instanceFileName, result.getResult(), result.getRuntime()));
        if (!result.getResult().isConclusive()) {
            log.info("Adding problem " + instanceFileName + " to the timeout queue");
            jedis.rpush(RedisUtils.makeKey(queueName, RedisUtils.TIMEOUTS_QUEUE), activeProblemFullPath);
        }
        writeMetrics(instanceFileName);
        final long numDeleted = jedis.lrem(RedisUtils.makeKey(queueName, RedisUtils.PROCESSING_QUEUE), 1, activeProblemFullPath);
        if (numDeleted != 1) {
            log.error("Couldn't delete problem " + activeProblemFullPath + " from the processing queue!");
        }
    }

    @Override
    public void onFinishedAllProblems() {
        SATFCMetrics.report();
    }

    private void writeMetrics(String srpkname) {
        final String json = JSONUtils.toString(SATFCMetrics.getMetrics());
        jedis.set(RedisUtils.makeKey(queueName, "METRICS", srpkname), json);
        SATFCMetrics.clear();
    }

}
