package ca.ubc.cs.beta.stationpacking.utils;

import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.jnalibraries.Clasp3Library;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * Created by newmanne on 20/05/15.
 */
public class NativeUtils {

    private final static int RTLD_LOCAL = 0x00000;
    private final static int RTLD_LAZY = 0x00001;

    public static final Map NATIVE_OPTIONS = ImmutableMap.of(Clasp3Library.OPTION_OPEN_FLAGS, RTLD_LAZY | RTLD_LOCAL);


}
