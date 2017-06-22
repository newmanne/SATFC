package ca.ubc.cs.beta.fcc.simulator.utils;

import com.google.common.base.Preconditions;

import java.util.Random;

/**
 * Created by newmanne on 2016-08-02.
 */
public class RandomUtils {

    public static Random random;

    public static void setRandom(long seed) {
        random = new Random(seed);
    }
    public static Random getRandom() {
        Preconditions.checkNotNull(random, "Seed not set!");
        return random;
    }

}
