package ca.ubc.cs.beta.fcc.simulator.solver;

import ca.ubc.cs.beta.fcc.simulator.solver.callback.SATFCCallback;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SimulatorResult;
import ca.ubc.cs.beta.fcc.simulator.solver.problem.SimulatorProblem;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader.SimulatorMessage;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;
import ca.ubc.cs.beta.stationpacking.utils.RedisUtils;
import ca.ubc.cs.beta.stationpacking.utils.Watch;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import redis.clients.jedis.Jedis;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
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
    // Used to know whether killAllProblems is necessary or whether things were just solved through decorators
    private final AtomicBoolean dirtyBit;
    private long careAboutId;
    private final int nWorkers;

    private final AtomicBoolean externalWakeup;

    public DistributedFeasibilitySolver(Jedis jedis, String sendQueue, String replyQueue, int nWorkers) {
        this.jedis = jedis;
        this.sendQueue = sendQueue;
        this.replyQueue = replyQueue;
        this.nWorkers = nWorkers;
        callbacks = new ConcurrentHashMap<>();
        id = new AtomicLong();
        cleanup();
        externalWakeup = new AtomicBoolean(false);
        dirtyBit = new AtomicBoolean(false);
    }

    private void cleanup() {
        jedis.del(replyQueue, sendQueue, RedisUtils.processing(sendQueue), RedisUtils.interrupt(sendQueue));
    }

    @Override
    public void getFeasibility(SimulatorProblem simulatorProblem, SATFCCallback callback) {
        // Push a job onto the queue
        dirtyBit.set(true);
        final long problemID = id.getAndIncrement();
        final String json = JSONUtils.toString(new SimulatorMessage(simulatorProblem.getSATFCProblem(), replyQueue, problemID));
        log.trace("Sending problem {} to queue {}", json, sendQueue);
        jedis.lpush(sendQueue, json);
        callbacks.put(problemID, new ProblemCallback(simulatorProblem, callback));
    }

    @Override
    public void waitForAllSubmitted() {
        // TODO: Add a check for stuckness, by remembering how much time?
        final SimulatorProblemReader.ExponentialBackoffWait waiter = new SimulatorProblemReader.ExponentialBackoffWait();
        if (!callbacks.isEmpty()) {
            log.info("Starting to wait for {} callbacks to complete", callbacks.size());
        }
        final Watch loggingWatch = Watch.constructAutoStartWatch();
        while (!externalWakeup.get() && !callbacks.isEmpty()) {
            if (loggingWatch.getElapsedTime() > 30) {
                log.info("Still waiting for {} callbacks to complete", callbacks.size());
                loggingWatch.reset();
                loggingWatch.start();
            }
            // Poll the reply queue
            final String answerString = jedis.lpop(replyQueue);
            if (answerString == null) {
                waiter.waitSleep();
                continue;
            }
            waiter.reset();
            final SimulatorProblemReader.SATFCSimulatorReply reply = JSONUtils.toObject(answerString, SimulatorProblemReader.SATFCSimulatorReply.class);
            if (reply.getId() >= careAboutId) {
                final ProblemCallback problemCallback = callbacks.remove(reply.getId());
                if (problemCallback == null) {
                    log.debug("Problem callback did not exist for reply {}. Maybe it was duplicated work?", reply.getId());
                    continue;
                }
                problemCallback.getCallback().onSuccess(problemCallback.getProblem(), SimulatorResult.fromSATFCResult(reply.getResult()));
            } else {
                log.trace("Discarding result for old problem {} since we no longer care about it", reply.getId());
            }
        }
        if (externalWakeup.compareAndSet(true, false)) {
            // Make sure the slate is clean
            killAll();
        }
    }

    @Override
    public void close() throws Exception {
        jedis.set(RedisUtils.interrupt(sendQueue), "DIE");
        jedis.close();
    }

    public void killAll() {
        if (dirtyBit.compareAndSet(true, false)) {
            final SimulatorProblemReader.ExponentialBackoffWait waiter = new SimulatorProblemReader.ExponentialBackoffWait();
            careAboutId = id.get();
            callbacks.clear();
            cleanup();
            final String stopKey = "STOP:" + RandomStringUtils.randomAlphanumeric(10);
            jedis.set(RedisUtils.interrupt(sendQueue), stopKey);
            final Watch watch = Watch.constructAutoStartWatch();
            while (true) {
                // Wait for all of the NWorkers to acknowledge the stop, then return
                if (Integer.toString(nWorkers).equals(jedis.get(stopKey))) {
                    break;
                }
                if (watch.getElapsedTime() > 5 * 60) {
                    throw new IllegalStateException("Waited more than 5 minutes and not every worker has acknowledged a stop!");
                }
                waiter.waitSleep();
            }
            cleanup();
            jedis.del(stopKey);
        }
    }

    public void wakeUp() {
        externalWakeup.set(true);
    }

    public void clearWakeUp() {
        externalWakeup.set(false);
    }


    @Data
    public static class ProblemCallback {

        private final SimulatorProblem problem;
        private final SATFCCallback callback;

    }

}
