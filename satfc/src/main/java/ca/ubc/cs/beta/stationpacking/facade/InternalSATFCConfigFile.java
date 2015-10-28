package ca.ubc.cs.beta.stationpacking.facade;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
* Created by newmanne on 14/10/15.
 * Portfolios that come bundled with SATFC (inside the jar)
*/
@RequiredArgsConstructor
public enum InternalSATFCConfigFile {
    SATFC_SEQUENTIAL("satfc_sequential"),
    SATFC_PARALLEL("satfc_parallel");

    @Getter
    private final String filename;
}
