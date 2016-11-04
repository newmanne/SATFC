//package ca.ubc.cs.beta.fcc.simulator.valuations;
//
//import ca.ubc.cs.beta.fcc.simulator.parameters.SimulatorParameters;
//import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
//import ca.ubc.cs.beta.fcc.simulator.station.StationInfo;
//import ca.ubc.cs.beta.fcc.simulator.utils.RandomUtils;
//import ca.ubc.cs.beta.fcc.simulator.utils.SimulatorUtils;
//import lombok.Data;
//import org.apache.commons.csv.CSVRecord;
//import org.apache.commons.math.util.FastMath;
//import org.apache.commons.math3.distribution.NormalDistribution;
//
//import java.util.HashMap;
//import java.util.Map;
//import java.util.Random;
//import java.util.Set;
//
///**
// * Created by newmanne on 2016-11-03.
// */
//public class MaxCFStickValues implements SimulatorParameters.IValueCalculator {
//
//    final String FAC_ID = "FacID";
//    final String MEAN_CF = "MeanCF";
//    final String MEAN_CF_MULTIPLES = "MeanCFMultiples";
//    final String MEAN_LOG_STICK = "MeanLogStick";
//
//    @Data
//    public static class StationParams {
//    }
//
//    private Map<Integer, ValueGenerator> stationToGenerator;
//
//    public MaxCFStickValues(String csvFile) {
//        stationToGenerator = new HashMap<>();
//        final Iterable<CSVRecord> records = SimulatorUtils.readCSV(csvFile);
//        for (CSVRecord record : records) {
//            final int id = Integer.parseInt(record.get(FAC_ID));
//            final double meanCF = Double.parseDouble(record.get(MEAN_CF));
//            final double meanCFMultiples = Double.parseDouble(record.get(MEAN_CF_MULTIPLES));
//            final double meanLogStick = Double.parseDouble(MEAN_LOG_STICK);
//            stationToGenerator.put(id, )
//        }
//
//    }
//
//    @Override
//    public void setValues(Set<StationInfo> stationInfos) {
//
//
//    }
//
//    /**
//     * See Doraszelski, Ulrich, et al. "Ownership Concentration and Strategic Supply Reduction." (2016).
//     */
//    public static class ValueGenerator {
//
//        public static final double STD_CASH_FLOW = 1.029461;
//        public static final double STD_CASH_FLOW_MULTIPLE = 0.976377254629814;
//        public static final double STD_LOG_STICK = 0.444053814276261;
//
//        NormalDistribution cashFlow;
//        NormalDistribution cashFlowMultiple;
//        NormalDistribution logStick;
//
//        Random random;
//
//        IStationInfo stationInfo;
//
//        public ValueGenerator(IStationInfo stationInfo, double meanCF, double meanCFMultiple, double meanLogStick) {
//            cashFlow = new NormalDistribution(meanCF, STD_CASH_FLOW);
//            cashFlowMultiple = new NormalDistribution(meanCFMultiple, STD_CASH_FLOW_MULTIPLE);
//            logStick = new NormalDistribution(meanLogStick, STD_LOG_STICK);
//            random = RandomUtils.getRandom();
//            this.stationInfo = stationInfo;
//        }
//
//        public double sampleValue() {
//            final double cf = cashFlow.inverseCumulativeProbability(random.nextDouble());
//            final double cfMultiple = cashFlowMultiple.inverseCumulativeProbability(random.nextDouble());
//            final double cfVal = cf * cfMultiple;
//
//            final double logStickVal = logStick.inverseCumulativeProbability(random.nextDouble());
//            final int pop = stationInfo.getPopulation();
//            final double stickVal = (FastMath.exp(logStickVal) * 6 * pop) / 1e6;
//
//            return Math.max(cfVal, stickVal);
//        }
//
//    }
//
//}
