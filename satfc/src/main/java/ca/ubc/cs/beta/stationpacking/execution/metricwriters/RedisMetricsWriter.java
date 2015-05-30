package ca.ubc.cs.beta.stationpacking.execution.metricwriters;

import ca.ubc.cs.beta.stationpacking.metrics.InstanceInfo;
import ca.ubc.cs.beta.stationpacking.metrics.SATFCMetrics;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;
import ca.ubc.cs.beta.stationpacking.utils.RedisUtils;
import redis.clients.jedis.Jedis;

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
        final InstanceInfo instanceInfo = SATFCMetrics.getMetrics();
        final String json = JSONUtils.toString(instanceInfo);
        jedis.set(RedisUtils.makeKey(queueName, "METRICS", instanceInfo.getName()), json);
    }

    @Override
    public void onFinished() {
        SATFCMetrics.report();
    }

}
