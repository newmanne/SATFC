package ca.ubc.cs.beta.fcc.simulator.solver;

import ca.ubc.cs.beta.fcc.simulator.Simulator;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SATFCCallback;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by newmanne on 2016-05-20.
 */
@Slf4j
public class DistributedSolver extends ASolver {

    @Data
    public static class ProblemCallback {

        private final Simulator.SATFCProblemSpecification problem;
        private final SATFCCallback callback;

    }

    @AllArgsConstructor
    @Data
    public static class SimulatorMessage {

        Simulator.SATFCProblemSpecification problemSpec;
        String replyQueue;
        String id;

    }

    private final ConcurrentHashMap<String, ProblemCallback> callbacks;
    private final Jedis jedis;
    private final String sendQueue;
    private final String replyQueue;

    public DistributedSolver(Simulator.ISATFCProblemSpecGenerator problemSpecGenerator, Jedis jedis, String sendQueue, String replyQueue) {
        super(problemSpecGenerator);
        this.jedis = jedis;
        this.sendQueue = sendQueue;
        this.replyQueue = replyQueue;
        callbacks = new ConcurrentHashMap<>();
        // Empty out the queues
        jedis.del(sendQueue);
        jedis.del(replyQueue);
    }

    @Override
    protected void solve(Simulator.SATFCProblemSpecification problemSpecification, SATFCCallback callback) {
        final String name = RandomStringUtils.randomAlphanumeric(10);
        final String json = JSONUtils.toString(new SimulatorMessage(problemSpecification, replyQueue, name));
        log.trace("Sending problem %s to queue %s", name, json);
        jedis.lpush(sendQueue, json);
        callbacks.put(name, new ProblemCallback(problemSpecification, callback));
    }

    @Override
    public void waitForAllSubmitted() {
        while (!callbacks.isEmpty()) {
            while (true) {
                // Poll the reply queue
                final String answerString = jedis.lpop(replyQueue);
                if (answerString == null) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    continue;
                }
                try {
                    final JsonNode jsonNode = JSONUtils.getMapper().readTree(answerString);
                    String id = jsonNode.get("id").asText();
                    SATFCResult answer = JSONUtils.getMapper().treeToValue(jsonNode.get("result"), SATFCResult.class);
                    final ProblemCallback problemCallback = callbacks.remove(id);
                    problemCallback.getCallback().onSuccess(problemCallback.getProblem(), answer);
                    break;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

}
