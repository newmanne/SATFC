package ca.ubc.cs.beta.stationpacking.execution;

import ca.ubc.cs.beta.stationpacking.execution.problemgenerators.SATFCFacadeProblem;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;
import ca.ubc.cs.beta.stationpacking.utils.RedisUtils;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Preconditions;
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
    private String activeProblemKey;
    private SimulatorMessage activeMessage;

    public SimulatorProblemReader(Jedis jedis, String queueName) {
        this.jedis = jedis;
        this.queueName = queueName;
        log.info("Reading instances from queue {}", RedisUtils.makeKey(queueName));
    }

    @Override
    public SATFCFacadeProblem getNextProblem() {
        SATFCFacadeProblem problem = null;
        while (true) {
            activeProblemKey = jedis.rpoplpush(RedisUtils.makeKey(queueName), RedisUtils.processing(queueName));
            if (activeProblemKey == null) {
                // Look at the first job in the processing queue. Could result in multiple workers doing the job, but that's OK. It's for errors anyways.
                activeProblemKey = jedis.lindex(RedisUtils.processing(queueName), 0);
                if (activeProblemKey == null) {
                    // Need to wait for a problem to appear
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    continue;
                } else if (activeProblemKey.equals("DIE")) {
                    log.info("Received a death signal, quitting!");
                    return null; // All done
                }
            }

            final String activeProblemString = jedis.get(activeProblemKey);
            // Race condition...
            if (activeProblemString == null) {
                continue;
            }
            activeMessage = JSONUtils.toObject(activeProblemString, SimulatorMessage.class);

            problem = new SATFCFacadeProblem(
                    null,
                    null,
                    activeMessage.getProblemSpec().getProblem().getDomains(),
                    activeMessage.getProblemSpec().getProblem().getPreviousAssignment(),
                    activeMessage.getProblemSpec().getStationInfoFolder(),
                    Long.toString(activeMessage.getId()),
                    activeMessage.getProblemSpec().getCutoff()
            );
            break;
        }
        return problem;
    }

    @Override
    public void onPostProblem(SATFCFacadeProblem problem, SATFCResult result) {
        super.onPostProblem(problem, result);

        // Put the reply back!
        jedis.lpush(activeMessage.getReplyQueue(), JSONUtils.toString(new SATFCSimulatorReply(result, activeMessage.getId())));

        final long numDeleted = jedis.lrem(RedisUtils.processing(queueName), 1, activeProblemKey);
        if (numDeleted != 1) {
            log.error("Couldn't delete problem {} from the processing queue!", activeProblemKey);
        }

    }

    // TODO: actually write proper json constructors for immutability

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class SATFCSimulatorReply {

        private SATFCResult result;
        private long id;

    }


    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class SimulatorMessage {

        SATFCProblemSpecification problemSpec;
        String replyQueue;
        long id;

    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SATFCProblemSpecification {

        private SATFCProblem problem;
        private double cutoff;
        private String stationInfoFolder;
        private long seed;
        private String name;

    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class SATFCProblem {

        private Map<Integer, Set<Integer>> domains;
        private Map<Integer, Integer> previousAssignment;

    }

}

