package ca.ubc.cs.beta.fcc.simulator.solver;

import ca.ubc.cs.beta.fcc.simulator.Simulator;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SATFCCallback;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader.SATFCProblemSpecification;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader.SimulatorMessage;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;
import com.google.common.base.Preconditions;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by newmanne on 2016-05-20.
 */
@Slf4j
public class DistributedFeasibilitySolver extends AFeasibilitySolver {

    private final ConcurrentHashMap<Long, ProblemCallback> callbacks;
    private final Jedis jedis;
    private final String sendQueue;
    private final String replyQueue;

    private final AtomicLong id;

    public DistributedFeasibilitySolver(Simulator.ISATFCProblemSpecGenerator problemSpecGenerator, Jedis jedis, String sendQueue, String replyQueue) {
        super(problemSpecGenerator);
        this.jedis = jedis;
        this.sendQueue = sendQueue;
        this.replyQueue = replyQueue;
        callbacks = new ConcurrentHashMap<>();
        id = new AtomicLong();
        // Empty out the queues
        jedis.del(sendQueue);
        jedis.del(replyQueue);
    }

    private String makeProblemKey(long id) {
        return sendQueue + ":" + id;
    }

    @Override
    protected void solve(SATFCProblemSpecification problemSpecification, SATFCCallback callback) {
        final long problemID = id.getAndIncrement();
        final String json = JSONUtils.toString(new SimulatorMessage(problemSpecification, replyQueue, problemID));
        log.trace("Sending problem {} to queue {}", json, sendQueue);
        jedis.set(makeProblemKey(problemID), json);
        jedis.lpush(sendQueue, Long.toString(problemID));
        callbacks.put(problemID, new ProblemCallback(problemSpecification, callback));
    }

    @Override
    public void waitForAllSubmitted() {
        while (!callbacks.isEmpty()) {
            while (true) {
                log.info("Waiting for {} callbacks to complete", callbacks.size());
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
                final SimulatorProblemReader.SATFCSimulatorReply reply = JSONUtils.toObject(answerString, SimulatorProblemReader.SATFCSimulatorReply.class);
                final ProblemCallback problemCallback = callbacks.remove(reply.getId());
                Preconditions.checkState(problemCallback != null, "Problem callback did not exist for reply %s", reply.getId());
                final String problemKey = makeProblemKey(reply.getId());
                log.trace("Deleting problem at key {}", problemKey);
                jedis.del(problemKey);
                problemCallback.getCallback().onSuccess(problemCallback.getProblem(), reply.getResult());
                break;
            }
        }
    }

    @Override
    public void close() throws Exception {

    }

    @Data
    public static class ProblemCallback {

        private final SATFCProblemSpecification problem;
        private final SATFCCallback callback;

    }

}
