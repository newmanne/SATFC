package ca.ubc.cs.beta.stationpacking.metrics;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;

import com.codahale.metrics.*;
import com.codahale.metrics.jvm.BufferPoolMetricSet;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.codahale.metrics.jvm.ThreadStatesGaugeSet;
import com.google.common.collect.Maps;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.lang.management.ManagementFactory;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
/**
 * Created by newmanne on 15/01/15.
 */
@Slf4j
public class SATFCMetrics {

    public final static int BLOCK_SIZE = 500;

    private static MetricHandler metricsHandler;
    private static final EventBus eventBus = new EventBus();

    public static void init() {
        metricsHandler = new MetricHandler();
        eventBus.register(metricsHandler);
        registerAll("gc", new GarbageCollectorMetricSet(), registry);
        registerAll("buffers", new BufferPoolMetricSet(ManagementFactory.getPlatformMBeanServer()), registry);
        registerAll("memory", new MemoryUsageGaugeSet(), registry);
        registerAll("threads", new ThreadStatesGaugeSet(), registry);
    }

    public static void postEvent(Object event) {
        eventBus.post(event);
    }
    
    public static Collection<InstanceInfo> getMetrics() {
    	return metricsHandler.getMetrics().stream().filter(metric -> !metric.getName().contains("component")).collect(Collectors.toList());
    }

    public static void clear() {
        metricsHandler.clear();
    }

    @Data
    public static class NewStationPackingInstanceEvent {
        private final Set<Integer> stations;
        private final String name;
    }

    @Data
    public static class InstanceSolvedEvent {
        private final String name;
        private final SATResult result;
        private final double runtime;
    }

    @Data
    public static class UnderconstrainedStationsRemovedEvent {
        private final String name;
        private final Set<Station> underconstrainedStations;
    }

    @Data
    public static class SplitIntoConnectedComponentsEvent {
        private final String name;
        private final Collection<StationPackingInstance> components;
    }

    @Data
    public static class TimingEvent {
        public final static String FIND_SUPERSET = "find_superset";
        public final static String FIND_SUBSET = "find_subset";
        public final static String FIND_UNDERCONSTRAINED_STATIONS = "find_underconstrained_stations";
        public final static String CONNECTED_COMPONENTS = "split_connected_components";

        private final String name;
        private final String timedEvent;
        private final double time;
    }

    @Data
    public static class SolvedByEvent {
        public final static String PRESOLVER = "presolver";
        public final static String CACHE_HIT = "cache_hit";
        public final static String SUBSET_CACHE = "subset_cache";

        private final String name;
        private final String solvedBy;
    }

    public static class MetricHandler {

    	private final Map<String, InstanceInfo> metrics = Maps.newLinkedHashMap();

        public void clear() {
            metrics.clear();
        }
        
        public Collection<InstanceInfo> getMetrics() {
        	return metrics.values();
        }

        @Subscribe
        public void onNewStationPackingInstanceEvent(NewStationPackingInstanceEvent event) {
            final InstanceInfo currentMetric = new InstanceInfo();
            currentMetric.setName(event.getName());
            currentMetric.setStations(event.getStations());
            currentMetric.setNumStations(event.getStations().size());
            metrics.put(event.getName(), currentMetric);
        }

        @Subscribe
        public void onInstanceSolvedEvent(InstanceSolvedEvent event) {
            final InstanceInfo instanceInfo = metrics.get(event.getName());
            instanceInfo.setResult(event.getResult());
            instanceInfo.setRuntime(event.getRuntime());
        }

        @Subscribe
        public void onUnderconstrainedStationsRemovedEvent(UnderconstrainedStationsRemovedEvent event) {
            final InstanceInfo instanceInfo = metrics.get(event.getName());
            instanceInfo.setUnderconstrainedStations(event.getUnderconstrainedStations().stream().map(Station::getID).collect(Collectors.toSet()));
        }

        @Subscribe
        public void onSplitIntoConnectedComponentsEvent (SplitIntoConnectedComponentsEvent event) {
            final InstanceInfo outerInstance = metrics.get(event.getName());
            event.getComponents().forEach(component -> {
                final InstanceInfo instanceInfo = new InstanceInfo();
                metrics.put(component.getName(), instanceInfo);
                instanceInfo.setName(component.getName());
                instanceInfo.setNumStations(component.getStations().size());
                instanceInfo.setStations(component.getStations().stream().map(Station::getID).collect(Collectors.toSet()));
                outerInstance.getComponents().add(instanceInfo);
            });
        }

        @Subscribe
        public void onSolvedByEvent(SolvedByEvent event) {
            metrics.get(event.getName()).setSolvedBy(event.getSolvedBy());
        }

        @Subscribe
        public void onTimingEvent(TimingEvent event) {
            final InstanceInfo instanceInfo = metrics.get(event.getName());
            if (instanceInfo.getTimingInfo() == null) {
                instanceInfo.setTimingInfo(Maps.newHashMap());
            }
            instanceInfo.getTimingInfo().put(event.getTimedEvent(), event.getTime());
        }
        
    }

    // codahale metrics for jvm stuff:
    private final static MetricRegistry registry = new MetricRegistry();
    // log jvm metrics
    public static void report() {
        log.info("Reporting jvm metrics");
        final Slf4jReporter reporter = Slf4jReporter.forRegistry(registry)
                                .outputTo(log)
                                .convertRatesTo(TimeUnit.SECONDS)
                                .convertDurationsTo(TimeUnit.MILLISECONDS)
                                .build();
                reporter.report();
    }
    private static void registerAll(String prefix, MetricSet metricSet, MetricRegistry registry) {
        for (Map.Entry<String, Metric> entry : metricSet.getMetrics().entrySet()) {
            if (entry.getValue() instanceof MetricSet) {
                registerAll(prefix + "." + entry.getKey(), (MetricSet) entry.getValue(), registry);
            } else {
                registry.register(prefix + "." + entry.getKey(), entry.getValue());
            }
        }
    }


}
