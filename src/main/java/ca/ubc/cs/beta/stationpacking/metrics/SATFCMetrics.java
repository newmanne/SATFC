package ca.ubc.cs.beta.stationpacking.metrics;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Slf4jReporter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
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

    @Getter
    private final static MetricRegistry registry = new MetricRegistry();
    
    private final static MetricHandler metricsHandler;
    private static final EventBus eventBus;

    static {
    	eventBus = new EventBus();
    	metricsHandler = new MetricHandler();
    	eventBus.register(metricsHandler);
    }

    public static void report() {
        final Slf4jReporter reporter = Slf4jReporter.forRegistry(registry)
                .outputTo(log)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();
        reporter.report();
    }
    
    public static void postEvent(Object event) {
        eventBus.post(event);
    }
    
    public static Collection<InstanceInfo> getMetrics() {
    	return metricsHandler.getMetrics().values();
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
    public static class SolvedByPresolverEvent {
        private final String name;
    }

    public static class MetricHandler {

    	@Getter
        private final Map<String, InstanceInfo> metrics = Maps.newHashMap();

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
        public void onSolvedByPresolverEvent(SolvedByPresolverEvent event) {
            metrics.get(event.getName()).setSolvedByPresolver(true);
        }

    }

    public static void clear() {
        metrics.clear();
    }
}
