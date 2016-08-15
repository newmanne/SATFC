//package ca.ubc.cs.beta.fcc.simulator.station;
//
//import ca.ubc.cs.beta.fcc.simulator.utils.Band;
//import ca.ubc.cs.beta.fcc.simulator.utils.BigDecimalUtils;
//import com.google.common.base.Preconditions;
//import com.google.common.collect.ImmutableSet;
//
//import java.math.BigDecimal;
//import java.util.Map;
//import java.util.Set;
//
///**
// * Created by newmanne on 2016-08-02.
// */
//public class MultimindedStation extends StationInfo {
//
//    private final Map<Band, BigDecimal> privateValues;
//
//    public MultimindedStation(int aID, Nationality nationality, BigDecimal volume, Band homeBand, Set<Integer> domain, Map<Band, BigDecimal> privateValues) {
//        super(aID, volume, privateValues.get(Band.OFF), nationality, homeBand, ImmutableSet.copyOf(domain));
//        Preconditions.checkState(BigDecimalUtils.isZero(privateValues.get(homeBand)), "Station should value its home band at 0");
//        this.privateValues = privateValues;
//    }
//
//    /*
//     * @return the station's utility of choosing the given band.
//     */
//    private BigDecimal getUtility(Band band, BigDecimal price) {
//        BigDecimal value = privateValues.get(band);
//        Preconditions.checkNotNull(value, "Station %s has no private value for band %s", this, band);
//        return price.subtract(value, BigDecimalUtils.MATH_CONTEXT);
//    }
//
//    @Override
//    public Band queryPreferredBand(Map<Band, BigDecimal> offers) {
//        return offers.entrySet().stream().max((moveA, moveB) -> {
//            int utilityComparison = getUtility(moveA.getKey(), moveA.getValue()).compareTo(getUtility(moveB.getKey(), moveB.getValue()));
//            // when in doubt, take the higher band
//            return utilityComparison == 0 ? moveA.getKey().compareTo(moveB.getKey()) : utilityComparison;
//        }).get().getKey();
//    }
//}
