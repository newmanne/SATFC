package ca.ubc.cs.beta.stationpacking.execution.metricwriters;

/**
 * Created by newmanne on 29/05/15.
 * This interface abstracts away where metrics are written to (to file, to redis, to stdout)
 */
public interface IMetricWriter {

    /**
     * Write metrics for a particular problem
     */
    void writeMetrics();

    void onFinished();
}
