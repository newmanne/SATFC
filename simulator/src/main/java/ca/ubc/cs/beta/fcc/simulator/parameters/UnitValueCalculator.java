package ca.ubc.cs.beta.fcc.simulator.parameters;

import ca.ubc.cs.beta.fcc.simulator.station.StationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.Set;

/**
 * Created by newmanne on 2016-10-04.
 */
public class UnitValueCalculator implements SimulatorParameters.IValueCalculator {

    @Override
    public void setValues(Set<StationInfo> stationInfos) {
        stationInfos.forEach(s -> {
            ImmutableMap.Builder<Band, Double> builder = ImmutableMap.builder();
            builder.put(Band.OFF, 0.);
            for (Band b : s.getHomeBand().getBandsBelowInclusive()) {
                if (b.equals(Band.OFF)) {
                    continue;
                }
                builder.put(b, 1.);
            }
            s.setValues(builder.build());
        });
    }

}
