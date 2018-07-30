package ca.ubc.cs.beta.stationpacking.execution;

import ca.ubc.cs.beta.aeatk.random.RandomUtil;
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
import org.apache.commons.lang3.RandomUtils;
import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

@Slf4j
public class SimulatorProblemReader extends AProblemReader {

    private final Jedis jedis;
    private final String queueName;
    private String activeProblemKey;
    private SimulatorMessage activeMessage;
    private final Set<String> ackedAlready;

    public SimulatorProblemReader(Jedis jedis, String queueName) {
        this.ackedAlready = new HashSet<>();
        this.jedis = jedis;
        this.queueName = queueName;
        log.info("Reading instances from queue {}", RedisUtils.makeKey(queueName));
    }

    @Override
    public SATFCFacadeProblem getNextProblem() {
        SATFCFacadeProblem problem;
        final ExponentialBackoffWait waiter = new ExponentialBackoffWait();
        while (true) {
            synchronized (jedis) {
                activeProblemKey = jedis.rpoplpush(RedisUtils.makeKey(queueName), RedisUtils.processing(queueName));
            }
            if (activeProblemKey == null) {
//                // Note: Not at all obvious you WANT the processing queue to be polled from in the Oct Alg use case b/c that means one of your workers went down... commenting out for now
//                // Look at the first job in the processing queue. Could result in multiple workers doing the job, but that's OK. It's for errors anyways.
//                activeProblemKey = jedis.lindex(RedisUtils.processing(queueName), 0);
                // Check for a DIE or STOP signal
                String interruptResult;
                synchronized (jedis) {
                    interruptResult = jedis.get(RedisUtils.interrupt(queueName));
                }
                if ("DIE".equals(interruptResult)) {
                    log.info("Received a death signal, quitting!");
                    return null; // All done
                } else if (interruptResult != null && interruptResult.startsWith("STOP") && !ackedAlready.contains(interruptResult)) {
                    log.trace("Acknowledging stop signal");
                    ackedAlready.add(interruptResult);
                    synchronized (jedis) {
                        jedis.incr(interruptResult);
                    }
                }

                // Need to wait for a problem to appear
                waiter.waitSleep();
                continue;
            }
            activeMessage = JSONUtils.toObject(activeProblemKey, SimulatorMessage.class);

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
        synchronized (jedis) {
            jedis.lpush(activeMessage.getReplyQueue(), JSONUtils.toString(new SATFCSimulatorReply(result, activeMessage.getId())));
            final long numDeleted = jedis.lrem(RedisUtils.processing(queueName), 1, activeProblemKey);
            if (numDeleted != 1) {
                // This can happen due to a killAll, so not a big deal
                log.trace("Couldn't delete problem {} from the processing queue!", activeProblemKey);
            }
        }


    }

    public boolean shouldInterrupt() {
        synchronized (jedis) {
            final String s = jedis.get(RedisUtils.makeKey(queueName) + ":INTERRUPT");
            return s != null && (s.startsWith("STOP") || s.equals("DIE"));
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

    @NoArgsConstructor
    public static class ExponentialBackoffWait {

        private int millis = RandomUtils.nextInt(5, 15);
        private int startMillis = millis;
        private int maxWait = 1000;

        public void waitSleep() {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            millis = Math.min(millis * 2, maxWait);
        }

        public void reset() {
            millis = startMillis;
        }

    }


}

