package ca.ubc.cs.beta.stationpacking.utils;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationDeserializer;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.YAMLBundle;
import com.fasterxml.jackson.databind.module.SimpleModule;

/**
* Created by newmanne on 05/10/15.
*/
public class SATFCJacksonModule extends SimpleModule {
    public SATFCJacksonModule() {
        addKeyDeserializer(Station.class, new StationDeserializer.StationClassKeyDeserializer());
        addDeserializer(YAMLBundle.ISolverConfig.class, new YAMLBundle.SolverConfigDeserializer());
        addDeserializer(YAMLBundle.IStationAddingStrategyConfig.class, new YAMLBundle.StationAddingStrategyConfigurationDeserializer());
        addDeserializer(YAMLBundle.IStationPackingConfigurationStrategyConfig.class, new YAMLBundle.PresolverConfigurationDeserializer());
    }
}
