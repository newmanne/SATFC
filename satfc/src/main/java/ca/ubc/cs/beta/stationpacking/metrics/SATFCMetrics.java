/**
 * Copyright 2015, Auctionomics, Alexandre Fréchette, Neil Newman, Kevin Leyton-Brown.
 *
 * This file is part of SATFC.
 *
 * SATFC is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SATFC is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SATFC.  If not, see <http://www.gnu.org/licenses/>.
 *
 * For questions, contact us at:
 * afrechet@cs.ubc.ca
 */
package ca.ubc.cs.beta.stationpacking.metrics;

import java.lang.management.ManagementFactory;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.Slf4jReporter;
import com.codahale.metrics.jvm.BufferPoolMetricSet;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.codahale.metrics.jvm.ThreadStatesGaugeSet;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

/**
 * Created by newmanne on 15/01/15.
 * TODO: if you solve the same problem twice in a row, metrics behaviour is undefined (because we use name to reference things. Oh well)
 */
@Slf4j
public class SATFCMetrics {

    private static MetricHandler metricsHandler;
    private static EventBus eventBus;

    public static void init() {
        metricsHandler = new MetricHandler();
        eventBus = new EventBus((exception, context) -> {
            log.error("Could not dispatch event: " + context.getSubscriber() + " to " + context.getSubscriberMethod(), exception);
        });
        eventBus.register(metricsHandler);
        registerAll("gc", new GarbageCollectorMetricSet(), registry);
        registerAll("buffers", new BufferPoolMetricSet(ManagementFactory.getPlatformMBeanServer()), registry);
        registerAll("memory", new MemoryUsageGaugeSet(), registry);
        registerAll("threads", new ThreadStatesGaugeSet(), registry);
    }

    public static void postEvent(Object event) {
        if (eventBus != null) {
            eventBus.post(event);
        }
    }

    public static void doWithMetrics(MetricHandler.IMetricCallback callback) {
        metricsHandler.doWithMetrics(callback);
    }

    public static void clear() {
        if (eventBus != null) {
            metricsHandler.clear();
        }
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
        public final static String SUBSET_CACHE = "subset_cache";
        public final static String SUPERSET_CACHE = "superset_cache";
        public final static String CLASP = "clasp";
        public final static String CONNECTED_COMPONENTS = "connected_components";

        private final String name;
        private final String solvedBy;
        private final SATResult result;
    }

    @Data
    public static class JustifiedByCacheEvent {
        private final String name;
        private final String key;
    }

    public static class MetricHandler {

        private InstanceInfo activeProblemMetrics;
        private Lock metricsLock = new ReentrantLock();

        public interface IMetricCallback {
            void doWithLock(InstanceInfo info);
        }

        private void safeMetricEdit(String name, IMetricCallback callback) {
            try {
                metricsLock.lock();
                // ensure that you only edit the "current problem" metrics.
                if (activeProblemMetrics != null && activeProblemMetrics.getName().startsWith(name)) {
                    final InstanceInfo info = getInfo(name);
                    if (info != null) {
                        callback.doWithLock(info);
                    }
                }
            } finally {
                metricsLock.unlock();
            }
        }

        public void doWithMetrics(IMetricCallback callback) {
            try {
                metricsLock.lock();
                callback.doWithLock(activeProblemMetrics);
            } finally {
                metricsLock.unlock();
            }
        }

        private void clear() {
            activeProblemMetrics = null;
        }

        private InstanceInfo getInfo(String name) {
            if (activeProblemMetrics == null) {
                throw new IllegalStateException("Trying to add metrics with no valid problem");
            } else if (name.equals(activeProblemMetrics.getName())) {
                return activeProblemMetrics;
            } else if (name.contains("_component")) {
                return activeProblemMetrics.getComponents().get(name);
            }
            return null; // This will catch presolver instances
        }

        @Subscribe
        public void onNewStationPackingInstanceEvent(NewStationPackingInstanceEvent event) {
            // don't thread this one
            if (activeProblemMetrics != null) {
                throw new IllegalStateException("Metrics already in progress!");
            }
            activeProblemMetrics = new InstanceInfo();
            activeProblemMetrics.setName(event.getName());
            activeProblemMetrics.setStations(event.getStations());
            activeProblemMetrics.setNumStations(event.getStations().size());
        }

        @Subscribe
        public void onInstanceSolvedEvent(InstanceSolvedEvent event) {
            safeMetricEdit(event.getName(), info -> {
                info.setResult(event.getResult());
                info.setRuntime(event.getRuntime());
            });
        }

        @Subscribe
        public void onUnderconstrainedStationsRemovedEvent(UnderconstrainedStationsRemovedEvent event) {
            safeMetricEdit(event.getName(), info -> {
                info.setUnderconstrainedStations(event.getUnderconstrainedStations().stream().map(Station::getID).collect(Collectors.toSet()));
            });
        }

        @Subscribe
        public void onSplitIntoConnectedComponentsEvent(SplitIntoConnectedComponentsEvent event) {
            safeMetricEdit(event.getName(), outerInfo -> {
                event.getComponents().forEach(component -> {
                    final InstanceInfo instanceInfo = new InstanceInfo();
                    outerInfo.getComponents().put(component.getName(), instanceInfo);
                    instanceInfo.setName(component.getName());
                    instanceInfo.setNumStations(component.getStations().size());
                    instanceInfo.setStations(component.getStations().stream().map(Station::getID).collect(Collectors.toSet()));
                });
            });
        }

        @Subscribe
        public void onSolvedByEvent(SolvedByEvent event) {
            safeMetricEdit(event.getName(), info -> {
                if (event.getSolvedBy() != null && event.getResult().isConclusive()) {
                    info.setSolvedBy(event.getSolvedBy());
                }
            });
        }

        @Subscribe
        public void onTimingEvent(TimingEvent event) {
            safeMetricEdit(event.getName(), info -> {
                info.getTimingInfo().put(event.getTimedEvent(), event.getTime());
            });
        }

        @Subscribe
        public void onJustifiedByCacheEvent(JustifiedByCacheEvent event) {
            safeMetricEdit(event.getName(), info -> {
                info.setCacheResultUsed(event.getKey());
            });
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