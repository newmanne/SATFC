package ca.ubc.cs.beta.fcc.simulator.utils;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.function.BinaryOperator;

/**
 * Created by newmanne on 2016-07-28.
 */
public class BigDecimalUtils {

    public final static MathContext MATH_CONTEXT = MathContext.DECIMAL128;
    public final static BinaryOperator<BigDecimal> SUM = (x, y) -> x.add(y, MATH_CONTEXT);
    public final static BigDecimal EPSILON = new BigDecimal("10e-6", MATH_CONTEXT);
    public final static boolean isPositive(BigDecimal d) { return d.compareTo(BigDecimal.ZERO) > 0; }
    public final static boolean isNonNegative(BigDecimal d) { return d.compareTo(BigDecimal.ZERO) >= 0; }
    public final static boolean isNegative(BigDecimal d) { return d.compareTo(BigDecimal.ZERO) < 0; }
    public final static boolean isZero(BigDecimal d) { return d.compareTo(BigDecimal.ZERO) == 0; }
    public final static BigDecimal bd(String d) { return new BigDecimal(d, MATH_CONTEXT); }
    public final static BigDecimal bdFromFrac(String frac) {
        List<String> split = Lists.newArrayList(Splitter.on('/').split(frac));
        return bd(split.get(0)).divide(bd(split.get(1)), MATH_CONTEXT);
    }
    public final static boolean epsilonEquals(BigDecimal x, BigDecimal y) {
        return x.subtract(y, MATH_CONTEXT).abs().compareTo(EPSILON) <= 0;
    }

}
