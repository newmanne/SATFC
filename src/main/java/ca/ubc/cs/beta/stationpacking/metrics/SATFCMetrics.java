package ca.ubc.cs.beta.stationpacking.metrics;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Slf4jReporter;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * Created by newmanne on 15/01/15.
 */
@Slf4j
public class SATFCMetrics {

    @Getter
    private final static MetricRegistry registry = new MetricRegistry();

    public static void report() {
        final Slf4jReporter reporter = Slf4jReporter.forRegistry(registry)
                .outputTo(log)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();
        reporter.report();
    }

}
