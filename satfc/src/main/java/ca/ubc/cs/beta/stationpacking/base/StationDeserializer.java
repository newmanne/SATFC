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
package ca.ubc.cs.beta.stationpacking.base;

import java.io.IOException;
import java.util.Map;

import ca.ubc.cs.beta.stationpacking.execution.parameters.smac.SATFCHydraParams;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.JSONBundle;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import static ca.ubc.cs.beta.stationpacking.execution.parameters.smac.SATFCHydraParams.SolverType.*;

/**
 * Created by newmanne on 06/03/15.
 */
public class StationDeserializer extends JsonDeserializer<Station> {

    @Override
    public Station deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException {
        return new Station(p.readValueAs(Integer.class));
    }

    public static class StationJacksonModule extends SimpleModule {
        public StationJacksonModule() {
            addKeyDeserializer(Station.class, new StationClassKeyDeserializer());
            addDeserializer(JSONBundle.SolverConfig.class, new SolverConfigDeserializer());
            addDeserializer(JSONBundle.IStationAddingStrategyConfig.class, new StationAddingStrategyConfigurationDeserializer());
            addDeserializer(JSONBundle.IStationPackingConfigurationStrategyConfig.class, new PresolverConfigurationDeserializer());
        }
    }

    public static class StationClassKeyDeserializer extends KeyDeserializer {
        @Override
        public Object deserializeKey(final String key,
                                     final DeserializationContext ctxt) throws IOException {
            return new Station(Integer.parseInt(key));
        }
    }

    @Slf4j
    public static class SolverConfigDeserializer extends JsonDeserializer<JSONBundle.SolverConfig> {

        final ImmutableMap<SATFCHydraParams.SolverType, Class<? extends JSONBundle.SolverConfig>> typeToConfigClass =
                ImmutableMap.<SATFCHydraParams.SolverType, Class<? extends JSONBundle.SolverConfig>>builder()
                        .put(CLASP, JSONBundle.ClaspConfig.class)
                        .put(UBCSAT, JSONBundle.UBCSATConfig.class)
                        .put(SAT_PRESOLVER, JSONBundle.SATPresolver.class)
                        .put(UNSAT_PRESOLVER, JSONBundle.UNSATPresolver.class)
                        .put(UNDERCONSTRAINED, JSONBundle.UnderconstrainedConfig.class)
                        .put(CONNECTED_COMPONENTS, JSONBundle.ConnectedComponentsConfig.class)
                        .put(ARC_CONSISTENCY, JSONBundle.ArcConsistencyConfig.class)
                        .put(VERIFIER, JSONBundle.AssignmentVerifierConfig.class)
                        .put(CACHE, JSONBundle.CacheConfig.class)
                        .put(SAT_CACHE, JSONBundle.SATCacheConfig.class)
                        .put(UNSAT_CACHE, JSONBundle.UNSATCacheConfig.class)
                        .put(PARALLEL, JSONBundle.ParallelConfig.class)
                        .put(RESULT_SAVER, JSONBundle.ResultSaverConfig.class)
                .build();


        @Override
        public JSONBundle.SolverConfig deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
            // Get the type form the name field
            JsonNode root = p.readValueAsTree();
            Preconditions.checkState(root.has("name"), "name is a required field!");
            final String name = root.get("name").asText();
            final SATFCHydraParams.SolverType type = SATFCHydraParams.SolverType.valueOf(name);
            final Class<? extends JSONBundle.SolverConfig> targetClass = typeToConfigClass.get(type);
            Preconditions.checkNotNull(targetClass, "No known conversion class for type %s", type);
            if (root.has("args")) {
                return JSONUtils.getMapper().treeToValue(root.get("args"), targetClass);
            } else {
                try {
                    return targetClass.newInstance();
                } catch (InstantiationException | IllegalAccessException e) {
                    throw new RuntimeException("Failed to instantiate an instance of " + targetClass.getSimpleName(), e);
                }
            }

        }

    }

    @Slf4j
    public static class PresolverConfigurationDeserializer extends JsonDeserializer<JSONBundle.IStationPackingConfigurationStrategyConfig> {

        Map<SATFCHydraParams.TimingChoice, Class<? extends JSONBundle.IStationPackingConfigurationStrategyConfig>> typeToConfig = ImmutableMap.<SATFCHydraParams.TimingChoice, Class<? extends JSONBundle.IStationPackingConfigurationStrategyConfig>>builder()
                .put(SATFCHydraParams.TimingChoice.ITERATIVE_DEEPEN, JSONBundle.IterativeDeepeningStrategyConfig.class)
                .build();

        @Override
        public JSONBundle.IStationPackingConfigurationStrategyConfig deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
            // TODO: repeated code
            JsonNode root = p.readValueAsTree();
            Preconditions.checkState(root.has("name"), "name is a required field!");
            final String name = root.get("name").asText();
            final SATFCHydraParams.TimingChoice type = SATFCHydraParams.TimingChoice.valueOf(name);
            final Class<? extends JSONBundle.IStationPackingConfigurationStrategyConfig> targetClass = typeToConfig.get(type);
            if (root.has("args")) {
                return JSONUtils.getMapper().treeToValue(root.get("args"), targetClass);
            } else {
                try {
                    return targetClass.newInstance();
                } catch (InstantiationException | IllegalAccessException e) {
                    throw new RuntimeException("Failed to instantiate an instance of " + targetClass.getSimpleName(), e);
                }
            }
        }

    }

    @Slf4j
    public static class StationAddingStrategyConfigurationDeserializer extends JsonDeserializer<JSONBundle.IStationAddingStrategyConfig> {

        Map<SATFCHydraParams.PresolverExpansion, Class<? extends JSONBundle.IStationAddingStrategyConfig>> typeToConfig = ImmutableMap.<SATFCHydraParams.PresolverExpansion, Class<? extends JSONBundle.IStationAddingStrategyConfig>>builder()
        .put(SATFCHydraParams.PresolverExpansion.NEIGHBOURHOOD, JSONBundle.NeighbourLayerConfig.class)
        .put(SATFCHydraParams.PresolverExpansion.UNIFORM_RANDOM, JSONBundle.AddRandomNeighbourConfig.class)
        .build();

        @Override
        public JSONBundle.IStationAddingStrategyConfig deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
            // TODO: repeated code
            JsonNode root = p.readValueAsTree();
            Preconditions.checkState(root.has("name"), "name is a required field!");
            final String name = root.get("name").asText();
            final SATFCHydraParams.PresolverExpansion type = SATFCHydraParams.PresolverExpansion.valueOf(name);
            final Class<? extends JSONBundle.IStationAddingStrategyConfig> targetClass = typeToConfig.get(type);
            if (root.has("args")) {
                return JSONUtils.getMapper().treeToValue(root.get("args"), targetClass);
            } else {
                try {
                    return targetClass.newInstance();
                } catch (InstantiationException | IllegalAccessException e) {
                    throw new RuntimeException("Failed to instantiate an instance of " + targetClass.getSimpleName(), e);
                }
            }
        }

    }

}