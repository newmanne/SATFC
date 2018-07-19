package ca.ubc.cs.beta.fcc.simulator.solver;

import ca.ubc.cs.beta.fcc.simulator.Simulator;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SATFCCallback;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SimulatorResult;
import ca.ubc.cs.beta.fcc.simulator.solver.problem.SimulatorProblem;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader.SATFCProblemSpecification;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader.SimulatorMessage;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;
import ca.ubc.cs.beta.stationpacking.utils.RedisUtils;
import ca.ubc.cs.beta.stationpacking.utils.Watch;
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

    public DistributedFeasibilitySolver(Jedis jedis, String sendQueue, String replyQueue) {
        this.jedis = jedis;
        this.sendQueue = sendQueue;
        this.replyQueue = replyQueue;
        callbacks = new ConcurrentHashMap<>();
        id = new AtomicLong();
        // Empty out the queues
        jedis.del(replyQueue);
        jedis.del(sendQueue);
        jedis.del(RedisUtils.processing(sendQueue));
    }

    private String makeProblemKey(long id) {
        return RedisUtils.makeKey(replyQueue, Long.toString(id));
    }

    @Override
    public void getFeasibility(SimulatorProblem simulatorProblem, SATFCCallback callback) {
        final long problemID = id.getAndIncrement();
        final String json = JSONUtils.toString(new SimulatorMessage(simulatorProblem.getSATFCProblem(), replyQueue, problemID));
        log.trace("Sending problem {} to queue {}", json, sendQueue);
        jedis.set(makeProblemKey(problemID), json);
        jedis.lpush(sendQueue, makeProblemKey(problemID));
        callbacks.put(problemID, new ProblemCallback(simulatorProblem, callback));
    }

    @Override
    public void waitForAllSubmitted() {
        if (!callbacks.isEmpty()) {
            log.info("Waiting for {} callbacks to complete", callbacks.size());
        }
        final Watch loggingWatch = Watch.constructAutoStartWatch();
        while (!callbacks.isEmpty()) {
            if (loggingWatch.getElapsedTime() > 60) {
                log.info("Waiting for {} callbacks to complete", callbacks.size());
                loggingWatch.reset();
                loggingWatch.start();
            }
            // Poll the reply queue
            final String answerString = jedis.lpop(replyQueue);
            if (answerString == null) {
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                continue;
            }
            final SimulatorProblemReader.SATFCSimulatorReply reply = JSONUtils.toObject(answerString, SimulatorProblemReader.SATFCSimulatorReply.class);
            final String problemKey = makeProblemKey(reply.getId());
            log.trace("Deleting problem at key {}", problemKey);
            jedis.del(problemKey);
            final ProblemCallback problemCallback = callbacks.remove(reply.getId());
            if (problemCallback == null) {
                log.debug("Problem callback did not exist for reply {}. Maybe it was duplicated work?", reply.getId());
                continue;
            }
            problemCallback.getCallback().onSuccess(problemCallback.getProblem(), SimulatorResult.fromSATFCResult(reply.getResult()));
        }
        jedis.del(sendQueue + ":INTERRUPT");
    }

    @Override
    public void close() throws Exception {
        jedis.lpush(sendQueue, "DIE");
        jedis.close();
    }

    public void killAll() {
        // TODO: Probably not this
        jedis.del(sendQueue);
        jedis.lpush(sendQueue + ":INTERRUPT", "STOP");
    }

    @Data
    public static class ProblemCallback {

        private final SimulatorProblem problem;
        private final SATFCCallback callback;

    }

}
