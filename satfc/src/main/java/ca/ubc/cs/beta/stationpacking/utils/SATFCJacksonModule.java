package ca.ubc.cs.beta.stationpacking.utils;

import com.fasterxml.jackson.databind.module.SimpleModule;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationDeserializer;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.YAMLBundle;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.yaml.ISolverConfig;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.yaml.IStationAddingStrategyConfig;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.yaml.IStationPackingConfigurationStrategyConfig;

/**
* Created by newmanne on 05/10/15.
 * A jackson module for all SATFC specific classes with custom serializer and deserializers
*/
public class SATFCJacksonModule extends SimpleModule {
    public SATFCJacksonModule() {
        addKeyDeserializer(Station.class, new StationDeserializer.StationClassKeyDeserializer());
        addDeserializer(ISolverConfig.class, new YAMLBundle.SolverConfigDeserializer());
        addDeserializer(IStationAddingStrategyConfig.class, new YAMLBundle.StationAddingStrategyConfigurationDeserializer());
        addDeserializer(IStationPackingConfigurationStrategyConfig.class, new YAMLBundle.PresolverConfigurationDeserializer());
    }
}
