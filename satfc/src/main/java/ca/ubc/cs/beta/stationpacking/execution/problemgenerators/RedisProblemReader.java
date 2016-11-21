/**
 * Copyright 2016, Auctionomics, Alexandre Fréchette, Neil Newman, Kevin Leyton-Brown.
 * <p>
 * This file is part of SATFC.
 * <p>
 * SATFC is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * SATFC is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with SATFC.  If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * For questions, contact us at:
 * afrechet@cs.ubc.ca
 */
package ca.ubc.cs.beta.stationpacking.execution.problemgenerators;

import java.io.IOException;

import ca.ubc.cs.beta.stationpacking.execution.AProblemReader;
import ca.ubc.cs.beta.stationpacking.execution.problemgenerators.problemparsers.IProblemParser;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
import ca.ubc.cs.beta.stationpacking.utils.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;

/**
 * Created by newmanne on 12/05/15.
 * Reads in problems from a redis queue, where each entry in the queue is a (full path) to an srpk file
 */
@Slf4j
public class RedisProblemReader extends AProblemReader {

    private final Jedis jedis;
    private final String queueName;
    private final IProblemParser nameToProblem;
    private String activeProblemDescription;

    public RedisProblemReader(Jedis jedis, String queueName, IProblemParser nameToProblem) {
        this.jedis = jedis;
        this.queueName = queueName;
        this.nameToProblem = nameToProblem;
        log.info("Reading instances from queue {}", RedisUtils.makeKey(queueName));
    }


    @Override
    public SATFCFacadeProblem getNextProblem() {
        SATFCFacadeProblem problem = null;
        String problemDescription;
        while (true) {
            problemDescription = jedis.rpoplpush(RedisUtils.makeKey(queueName), RedisUtils.makeKey(queueName, RedisUtils.PROCESSING_QUEUE));
            if (problemDescription == null) { // all problems exhausted
                return null;
            }

            try {
                problem = nameToProblem.problemFromName(problemDescription);
                break;
            } catch (IOException e) {
                log.warn("Error parsing file " + problemDescription + ", skipping it", e);
            }
        }

        final long remainingJobs = jedis.llen(RedisUtils.makeKey(queueName));
        log.info("There are {} problems remaining in the queue", remainingJobs);
        activeProblemDescription = problemDescription;
        return problem;
    }

    @Override
    public void onPostProblem(SATFCFacadeProblem problem, SATFCResult result) {
        super.onPostProblem(problem, result);
        // update redis queue - if the job timed out, move it to the timeout channel. Either way, delete it from the processing queue
        if (!result.getResult().isConclusive()) {
            log.info("Adding problem " + problem.getInstanceName() + " to the timeout queue");
            jedis.rpush(RedisUtils.makeKey(queueName, RedisUtils.TIMEOUTS_QUEUE), activeProblemDescription);
        }
        final long numDeleted = jedis.lrem(RedisUtils.makeKey(queueName, RedisUtils.PROCESSING_QUEUE), 1, activeProblemDescription);
        if (numDeleted != 1) {
            log.error("Couldn't delete problem " + activeProblemDescription + " from the processing queue!");
        }
    }


}