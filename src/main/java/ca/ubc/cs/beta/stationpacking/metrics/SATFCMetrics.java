package ca.ubc.cs.beta.stationpacking.metrics;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;

import com.codahale.metrics.*;
import com.codahale.metrics.jvm.BufferPoolMetricSet;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.codahale.metrics.jvm.ThreadStatesGaugeSet;
import com.google.common.collect.Maps;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.eventbus.SubscriberExceptionContext;
import com.google.common.eventbus.SubscriberExceptionHandler;

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

	private static MetricHandler metricsHandler;
    private static EventBus eventBus;

    public static void init() {
        metricsHandler = new MetricHandler();
        eventBus = new EventBus(new SubscriberExceptionHandler() {

            @Override
            public void handleException(Throwable exception,
                                        SubscriberExceptionContext context) {
                log.error("Could not dispatch event: " + context.getSubscriber() + " to " + context.getSubscriberMethod(), exception);
            }
        });
        eventBus.register(metricsHandler);
        registerAll("gc", new GarbageCollectorMetricSet(), registry);
        registerAll("buffers", new BufferPoolMetricSet(ManagementFactory.getPlatformMBeanServer()), registry);
        registerAll("memory", new MemoryUsageGaugeSet(), registry);
        registerAll("threads", new ThreadStatesGaugeSet(), registry);
    }

    public static void postEvent(Object event) {
        eventBus.post(event);
    }
    
    public static InstanceInfo getMetrics() {
    	return metricsHandler.getMetrics();
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
        public final static String HASHING = "hashing";
        public final static String TO_STRING = "to_string";
		public final static String BEST_CASE_PARALLEL_SOLVE_TIME = "best_case_parallel_solve_time";


        private final String name;
        private final String timedEvent;
        private final double time;
    }

    @Data
    public static class SolvedByEvent {
        public final static String PRESOLVER = "presolver";
        public final static String CACHE_HIT = "cache_hit";
        public final static String SUBSET_CACHE = "subset_cache";
        public final static String SUPERSET_CACHE = "superset_cache";
        public static final String CLASP = "clasp";

        private final String name;
        private final String solvedBy;
        private final SATResult result;
    }
    
    @Data
    public static class ComponentsSolvedEvent {
    	private final String name;
    	private final Collection<SolverResult> solverResults;
    	private final SATResult result;
	}


    public static class MetricHandler {

    	private final Map<String, InstanceInfo> metrics = Maps.newLinkedHashMap();

        public void clear() {
            metrics.clear();
        }
        
        public InstanceInfo getMetrics() {
        	return metrics.values().iterator().next();
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
            if (event.getResult().equals(SATResult.SAT) || event.getResult().equals(SATResult.UNSAT)) {
                metrics.get(event.getName()).setSolvedBy(event.getSolvedBy());
            }
        }

        @Subscribe
        public void onTimingEvent(TimingEvent event) {
            final InstanceInfo instanceInfo = metrics.get(event.getName());
            if (instanceInfo.getTimingInfo() == null) {
                instanceInfo.setTimingInfo(Maps.newHashMap());
            }
            instanceInfo.getTimingInfo().put(event.getTimedEvent(), event.getTime());
        }
        
        @Subscribe
        public void onComponentsSolvedEvent(ComponentsSolvedEvent event) {
        	if (event.getResult().equals(SATResult.SAT)) {
        		double time = event.getSolverResults().stream().mapToDouble(SolverResult::getRuntime).max().getAsDouble();
        		SATFCMetrics.postEvent(new TimingEvent(event.getName(), TimingEvent.BEST_CASE_PARALLEL_SOLVE_TIME, time));
        	} else if (event.getResult().equals(SATResult.UNSAT)) {
        		double time = event.getSolverResults().stream().mapToDouble(SolverResult::getRuntime).min().getAsDouble();
        		SATFCMetrics.postEvent(new TimingEvent(event.getName(), TimingEvent.BEST_CASE_PARALLEL_SOLVE_TIME, time));
        	}
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
