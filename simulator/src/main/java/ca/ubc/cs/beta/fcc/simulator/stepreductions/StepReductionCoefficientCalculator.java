package ca.ubc.cs.beta.fcc.simulator.stepreductions;

import ca.ubc.cs.beta.fcc.simulator.ladder.ILadder;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.fcc.simulator.utils.BigDecimalUtils;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import lombok.Data;
import org.apache.commons.math3.util.FastMath;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static ca.ubc.cs.beta.fcc.simulator.utils.BigDecimalUtils.MATH_CONTEXT;

/**
 * Created by newmanne on 2016-07-26.
 */

public class StepReductionCoefficientCalculator {

    private final Map<Band, Double> initialBenchmarkPayments;

    public StepReductionCoefficientCalculator(Map<Band, Double> initialBenchmarkPayments) {
        this.initialBenchmarkPayments = initialBenchmarkPayments;
    }

    /**
     * Computes step reduction coefficients.
     *
     * @param vacancies - per station, band vacancies.
     * @return a per station, band map of step reduction coefficients.
     */
    public ImmutableTable<IStationInfo, Band, Double> computeStepReductionCoefficients(Table<IStationInfo, Band, Double> vacancies, ILadder ladder) {
        final ImmutableTable.Builder<IStationInfo, Band, Double> builder = ImmutableTable.builder();

        final double initialHVHF = initialBenchmarkPayments.get(Band.HVHF);
        final double initialLVHF = initialBenchmarkPayments.get(Band.LVHF);
        final double initialOff = initialBenchmarkPayments.get(Band.OFF);

        final double vacancyResistance = 0.5;

        for (IStationInfo station : vacancies.rowKeySet()) {
            double vacU = vacancies.get(station, Band.UHF);

            // You always get the full decrement to go off air
            builder.put(station, Band.OFF, 1.0);

            // You don't decrement if you are going to UHF (because it's already 0 payment)
            builder.put(station, Band.UHF, 0.0);

            if (ladder.getBands().stream().anyMatch(Band::isVHF)) {
                double vacH = vacancies.get(station, Band.HVHF);
                double vacL = vacancies.get(station, Band.LVHF);

                final double rHVHF = (initialHVHF * (Math.pow(vacU, vacancyResistance))) / (((initialOff - initialHVHF) * Math.pow(vacH, vacancyResistance)) + (initialHVHF * (Math.pow(vacU, vacancyResistance))));
                builder.put(station, Band.HVHF, rHVHF);

                final double rLVHF = ((((initialLVHF - initialHVHF) * Math.pow(vacH, vacancyResistance)) / (((initialOff - initialLVHF) * Math.pow(vacL, vacancyResistance)) + ((initialLVHF - initialHVHF) * Math.pow(vacH, vacancyResistance)))) * (1 - rHVHF)) + rHVHF;
                builder.put(station, Band.LVHF, rLVHF);
            }
        }

        return builder.build();
    }

}