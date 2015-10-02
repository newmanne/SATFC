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
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.collect.ImmutableMap;
import lombok.Data;

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
        }
    }

    public static class StationClassKeyDeserializer extends KeyDeserializer {
        @Override
        public Object deserializeKey(final String key,
                                     final DeserializationContext ctxt) throws IOException {
            return new Station(Integer.parseInt(key));
        }
    }

    public static class SolverConfigDeserializer extends JsonDeserializer<JSONBundle.SolverConfig> {

        final ImmutableMap<SATFCHydraParams.SolverType, Class<? extends JSONBundle.SolverConfig>> typeToConfigClass =
                ImmutableMap.<SATFCHydraParams.SolverType, Class<? extends JSONBundle.SolverConfig>>builder()
                        .put(CLASP, JSONBundle.ClaspConfig.class)
                        .put(UBCSAT, JSONBundle.UBCSATConfig.class)
//                        .put(SAT_PRESOLVER, Sat)
//                        .put(UNSAT_PRESOLVER)
                        .put(UNDERCONSTRAINED, JSONBundle.UnderconstrainedConfig.class)
                        .put(CONNECTED_COMPONENTS, JSONBundle.ConnectedComponentsConfig.class)
                        .put(ARC_CONSISTENCY, JSONBundle.ArcConsistencyConfig.class)
                        .put(VERIFIER, JSONBundle.AssignmentVerifierConfig.class)
                        .put(CACHE, JSONBundle.CacheConfig.class)
                        .put(SAT_CACHE, JSONBundle.SATCacheConfig.class)
                        .put(UNSAT_CACHE, JSONBundle.UNSATCacheConfig.class)
                        .put(PARALLEL, JSONBundle.ParallelConfig.class)
                .build();


        @Override
        public JSONBundle.SolverConfig deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
            // Get the type form the name field
            JsonNode root = p.readValueAsTree();
            final SATFCHydraParams.SolverType type = SATFCHydraParams.SolverType.valueOf(root.get("name").toString());
            return JSONUtils.getMapper().treeToValue(root.get("args"), typeToConfigClass.get(type));
        }

    }

}
