package ca.ubc.cs.beta.stationpacking.metrics;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Slf4jReporter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
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
    
    
    // Relatively hacky ways of getting access to metric classes
    @Getter
    private final static List<InstanceInfo> metrics = Lists.newArrayList();
    
    public static InstanceInfo getMostRecentOutermostInstanceInfo() {
    	return Iterables.getLast(metrics);
    }

}
