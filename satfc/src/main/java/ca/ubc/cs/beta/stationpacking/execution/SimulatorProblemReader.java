package ca.ubc.cs.beta.stationpacking.execution;

import ca.ubc.cs.beta.stationpacking.execution.problemgenerators.SATFCFacadeProblem;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;
import ca.ubc.cs.beta.stationpacking.utils.RedisUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Builder;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@Slf4j
public class SimulatorProblemReader extends AProblemReader {

    private final Jedis jedis;
    private final String queueName;
    String activeProblemString;
    String replyQueue;
    String problemID;

    public SimulatorProblemReader(Jedis jedis, String queueName) {
        this.jedis = jedis;
        this.queueName = queueName;
        log.info("Reading instances from queue {}", RedisUtils.makeKey(queueName));
    }

    @Override
    public SATFCFacadeProblem getNextProblem() {
        SATFCFacadeProblem problem = null;
        while (true) {
            activeProblemString = jedis.rpoplpush(RedisUtils.makeKey(queueName), RedisUtils.makeKey(queueName, RedisUtils.PROCESSING_QUEUE));
            if (activeProblemString == null) {
                // Need to wait for a problem to appear
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                continue;
            }
            final SimulatorMessage simulatorMessage = JSONUtils.toObject(activeProblemString, SimulatorMessage.class);

            replyQueue = simulatorMessage.getReplyQueue();
            problemID = simulatorMessage.getId();
            problem = new SATFCFacadeProblem(
                    null,
                    null,
                    simulatorMessage.getProblemSpec().getProblem().getDomains(),
                    simulatorMessage.getProblemSpec().getProblem().getPreviousAssignment(),
                    simulatorMessage.getProblemSpec().getStationInfoFolder(),
                    simulatorMessage.getId(),
                    simulatorMessage.getProblemSpec().getCutoff()
            );
            break;
        }
        return problem;
    }


    // TODO: hacky copy over

    @Data
    public static class SimulatorMessage {

        SATFCProblemSpecification problemSpec;
        String replyQueue;
        String id;

    }

    @Data
    public static class SATFCProblemSpecification {

        private SATFCProblem problem;
        private String stationInfoFolder;
        private double cutoff;
        private long seed;

    }


    @Data
    public static class SATFCProblem {

        private Map<Integer, Set<Integer>> domains;
        private Map<Integer, Integer> previousAssignment;

    }

    @Override
    public void onPostProblem(SATFCFacadeProblem problem, SATFCResult result) {
        super.onPostProblem(problem, result);

        // I can see why this might be a bad idea, but probably it will just work...
        final long numDeleted = jedis.lrem(RedisUtils.makeKey(queueName, RedisUtils.PROCESSING_QUEUE), 1, activeProblemString);
        if (numDeleted != 1) {
            log.error("Couldn't delete problem %s from the processing queue!", problemID);
        }

        // Put the reply back!
        jedis.lpush(replyQueue, JSONUtils.toString(new SATFCSimulatorReply(result, problemID)));

    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class SATFCSimulatorReply {

        private SATFCResult result;
        private String id;

    }


}

