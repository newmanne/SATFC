package ca.ubc.cs.beta.stationpacking.execution.metricwriters;

import ca.ubc.cs.beta.stationpacking.execution.parameters.SATFCFacadeParameters;
import ca.ubc.cs.beta.stationpacking.metrics.SATFCMetrics;

/**
 * Created by newmanne on 29/05/15.
 */
public class MetricWriterFactory {

    public static IMetricWriter createFromParameters(SATFCFacadeParameters parameters) {
        if (parameters.fMetricsFile != null) {
            SATFCMetrics.init();
            return new FileMetricsWriter(parameters.fMetricsFile);
        } else if (parameters.fRedisParameters.areValid()) {
            SATFCMetrics.init();
            return new RedisMetricsWriter(parameters.fRedisParameters.getJedis(), parameters.fRedisParameters.fRedisQueue);
        } else {
            return new IMetricWriter() {
                @Override
                public void writeMetrics() {

                }

                @Override
                public void onFinished() {

                }
            };
        }
    }

}