package ca.ubc.cs.beta.fcc.simulator.parameters;

import ca.ubc.cs.beta.aeatk.misc.options.UsageTextField;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import com.beust.jcommander.Parameter;
import com.google.common.collect.ImmutableMap;
import lombok.Getter;

import java.util.Map;

/**
 * Created by newmanne on 2016-09-27.
 */
@UsageTextField(title = "MultiBand Simulator Parameters", description = "MultiBand Simulator Parameters")
public class MultiBandSimulatorParameters extends SimulatorParameters {

    @Getter
    @Parameter(names = "-R1", description = "Decrement by R1 * base clock")
    private double r1 = 0.05;

    @Getter
    @Parameter(names = "-R2", description = "Decrement by R2 * base clock at round 0")
    private double r2 = 0.01;

    @Getter
    @Parameter(names = "-VACANCY-FLOOR", description = "Minimum vacancy value")
    private double vacFloor = 0.5;

    @Getter
    @Parameter(names = "-UHF-TO-LVHF-FRAC", description = "Fraction of the UHF->OFF price per unit volume if a UHF station moves to LVHF")
    private double UHFToLVHFFrac = 0.75;

    @Getter
    @Parameter(names = "-UHF-TO-HVHF-FRAC", description = "Fraction of the UHF->OFF price per unit volume if a UHF station moves to HVHF")
    private double UHFToHVHFFrac = 0.4;

    @Override
    public void setUp() {
        super.setUp();
    }

    public Map<Band, Double> getOpeningBenchmarkPrices() {
        if (getLockVHFUntilBase()) {
            return ImmutableMap.of(Band.UHF, 0., Band.OFF, getUHFToOff(), Band.HVHF, getUHFToHVHFFrac() * Math.max(getUHFToOff(), SimulatorParameters.FCC_UHF_TO_OFF), Band.LVHF, getUHFToLVHFFrac() * Math.max(getUHFToOff(), SimulatorParameters.FCC_UHF_TO_OFF));
        }
        return ImmutableMap.of(Band.UHF, 0., Band.OFF, getUHFToOff(), Band.HVHF, getUHFToHVHFFrac() * getUHFToOff(), Band.LVHF, getUHFToLVHFFrac() * getUHFToOff());
    }

    public LadderAuctionParameters getLadderAuctionParameter() {
        return LadderAuctionParameters
                .builder()
                .r1(getR1())
                .r2(getR2())
                .openingBenchmarkPrices(getOpeningBenchmarkPrices())
                .VAC_FLOOR(getVacFloor())
                .build();
    }

}
