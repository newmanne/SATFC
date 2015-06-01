package ca.ubc.cs.beta.stationpacking.execution.metricwriters;

import java.io.File;
import java.io.IOException;

import lombok.extern.slf4j.Slf4j;
import ca.ubc.cs.beta.stationpacking.metrics.SATFCMetrics;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

/**
 * Created by newmanne on 29/05/15.
 */
@Slf4j
public class FileMetricsWriter implements IMetricWriter {

    private final File metricsFile;

    public FileMetricsWriter(String metricsFileName) {
        this.metricsFile = new File(metricsFileName);
        if (this.metricsFile.exists()) {
            this.metricsFile.delete();
        }
    }

    @Override
    public void writeMetrics() {
        SATFCMetrics.doWithMetrics(info -> {
            try {
                Files.append(JSONUtils.toString(info), metricsFile, Charsets.UTF_8);
            } catch (IOException e) {
                log.error("Couldn't save metrics to file " + metricsFile.getAbsolutePath(), e);
            }
        });
    }

    @Override
    public void onFinished() {
        SATFCMetrics.report();
    }

}
