package ca.ubc.cs.beta.stationpacking.execution.metricwriters;

import redis.clients.jedis.Jedis;
import ca.ubc.cs.beta.stationpacking.metrics.SATFCMetrics;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;
import ca.ubc.cs.beta.stationpacking.utils.RedisUtils;

/**
 * Created by newmanne on 29/05/15.
 */
public class RedisMetricsWriter implements IMetricWriter {

    private final Jedis jedis;
    private final String queueName;

    public RedisMetricsWriter(Jedis jedis, String queueName) {
        this.jedis = jedis;
        this.queueName = queueName;
    }

    @Override
    public void writeMetrics() {
        SATFCMetrics.doWithMetrics(info -> {
            final String json = JSONUtils.toString(info);
            jedis.set(RedisUtils.makeKey(queueName, "METRICS", info.getName()), json);
        });

    }

    @Override
    public void onFinished() {
        SATFCMetrics.report();
    }

}
