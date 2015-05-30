package ca.ubc.cs.beta.stationpacking.execution.metricwriters;

/**
 * Created by newmanne on 29/05/15.
 */
public interface IMetricWriter {

    /**
     * Write metrics for a particular problem
     */
    void writeMetrics();

    void onFinished();
}
