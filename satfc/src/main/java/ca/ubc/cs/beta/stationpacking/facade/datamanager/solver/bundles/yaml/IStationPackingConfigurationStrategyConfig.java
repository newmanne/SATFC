package ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.yaml;

import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.strategies.IStationPackingConfigurationStrategy;

/**
* Created by newmanne on 27/10/15.
*/
public interface IStationPackingConfigurationStrategyConfig {
    IStationPackingConfigurationStrategy createStrategy();
}
