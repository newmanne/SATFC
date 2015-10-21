package ca.ubc.cs.beta.stationpacking.facade;

import lombok.Data;
import lombok.experimental.Builder;

import java.util.concurrent.TimeUnit;

/**
* Created by newmanne on 16/10/15.
*  Options regarding the auto-augmentation feature, which can be used to expand a SATFCServer cache during downtime
*/
@Data
@Builder
public class AutoAugmentOptions {

    // How long should the SATFCFacade remain idle before beginning augmentation
    private double idleTimeBeforeAugmentation = 60 * 5;
    // Turn auto augmnent on / off
    private boolean augment = false;
    // The station configuration folder with the required files to auto augment from
    private String augmentStationConfigurationFolder;
    // How long to spend on each generated problem before giving up
    private double augmentCutoff = TimeUnit.HOURS.convert(3, TimeUnit.SECONDS);
    // How often SATFC should poll to see if the facade has been idle
    private double pollingInterval = 60 * 5;

}
