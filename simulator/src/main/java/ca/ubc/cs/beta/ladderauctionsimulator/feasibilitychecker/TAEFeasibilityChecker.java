//package ca.ubc.cs.beta.ladderauctionsimulator.feasibilitychecker;
//
//import java.io.File;
//import java.io.UnsupportedEncodingException;
//import java.security.MessageDigest;
//import java.util.Arrays;
//import java.util.Collections;
//import java.util.HashMap;
//import java.util.Iterator;
//import java.util.List;
//import java.util.Map;
//import java.util.Set;
//import java.util.concurrent.atomic.AtomicInteger;
//
//import ca.ubc.cs.beta.stationpacking.tae.SATFCTargetAlgorithmEvaluator;
//import org.apache.commons.codec.binary.Hex;
//import org.apache.commons.codec.digest.DigestUtils;
//import org.apache.commons.lang3.StringUtils;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import ca.ubc.cs.beta.aeatk.algorithmexecutionconfiguration.AlgorithmExecutionConfiguration;
//import ca.ubc.cs.beta.aeatk.algorithmrunconfiguration.AlgorithmRunConfiguration;
//import ca.ubc.cs.beta.aeatk.algorithmrunresult.AlgorithmRunResult;
//import ca.ubc.cs.beta.aeatk.parameterconfigurationspace.ParameterConfigurationSpace;
//import ca.ubc.cs.beta.aeatk.probleminstance.ProblemInstance;
//import ca.ubc.cs.beta.aeatk.probleminstance.ProblemInstanceSeedPair;
//import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluator;
//import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluatorCallback;
//import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
//
///**
// * Feasibility checker that delegates the solving of instances to a TAE.
// * @author afrechet
// */
//public class TAEFeasibilityChecker implements IFeasibilityChecker {
//
//    private static final Logger log = LoggerFactory.getLogger(TAEFeasibilityChecker.class);
//
//    private final TargetAlgorithmEvaluator fTAE;
//    private final AlgorithmExecutionConfiguration fAlgoExecConfig;
//    private final String fStationConfigFolder;
//
//    private final long fSeed;
//    private final double fCutoff;
//
//    /*
//     * The TAE that will receive runs from this object can check the run config for the TAE context and check that it is indeed solving
//     * feasibility checking problem. As of now, the TAE context is SATFC specific, as the latter is the only TAE solving feasibility checking problem explicitly.
//     */
//    private final static Map<String,String> TAE_CONTEXT = new HashMap<String,String>();
//    static
//    {
//        TAE_CONTEXT.put(SATFCTargetAlgorithmEvaluator.SATFC_CONTEXT_KEY, SATFCTargetAlgorithmEvaluator.SATFC_CONTEXT_KEY);
//    }
//
//    /**
//     * Construct a TAE feasiblity checker.
//     * @param aTAE - the target algorithm evaluator to use.
//     * @param aStationConfigFoldername - the name of the (last) folder in which to find interferences data. For example, if the full interference folder is "/home/data/hawaii/" then this option would be "hawaii".
//     * @param aCutoff - the cutoff time (s) for instances.
//     * @param aSeed - the seed to use for execution.
//     */
//    public TAEFeasibilityChecker(TargetAlgorithmEvaluator aTAE, String aStationConfigFoldername, double aCutoff, long aSeed)
//    {
//        if(aTAE==null)
//        {
//            throw new IllegalArgumentException("Target algorithm evaluator cannot be null.");
//        }
//        fTAE = aTAE;
//
//        if(aStationConfigFoldername == null)
//        {
//            throw new IllegalArgumentException("Station config folder name cannot be null.");
//        }
//        if(aStationConfigFoldername.contains(File.separator))
//        {
//            throw new IllegalArgumentException("Station config folder for TAE feasibility checker should only be a single folder name in which the TAE should"
//                    + "find the station config data. Typically the TAE will prepend its own station config folder location, as it may live on a different file system.");
//        }
//        fStationConfigFolder = aStationConfigFoldername;
//
//        fSeed = aSeed;
//
//        if(aCutoff <= 0 )
//        {
//            throw new IllegalArgumentException("Provided cutoff must be strictly positive.");
//        }
//        fCutoff = aCutoff;
//
//        fAlgoExecConfig = new AlgorithmExecutionConfiguration("", "", ParameterConfigurationSpace.getSingletonConfigurationSpace(), false, fCutoff,TAE_CONTEXT);
//    }
//
//    /**
//     * Pre-process a given feasibility checking problem for edge cases that we do not want to pass to the TAE (e.g. empty problems).
//     * @param aProblem - a feasibility checking problem.
//     * @return the feasibility checking result for the given problem if it was pre-processed, null otherwise.
//     */
//    private FeasibilityCheckerResult preprocess(FeasibilityCheckerProblem aProblem)
//    {
//        Map<Integer,Set<Integer>> problemMap = aProblem.getStationToDomainMapping();
//
//        if(problemMap.isEmpty())
//        {
//            log.trace("Feasibility checking problem is empty, trivially satisfiable.");
//            return new FeasibilityCheckerResult(SATResult.SAT, Collections.<Integer, Integer> emptyMap());
//        }
//        for(Set<Integer> domain : problemMap.values())
//        {
//            if(domain.isEmpty())
//            {
//                log.trace("Domain of a single stations is empty, trivially unsatisfiable.");
//                return new FeasibilityCheckerResult(SATResult.UNSAT, Collections.<Integer,Integer> emptyMap());
//            }
//        }
//        return null;
//    }
//
//    @Override
//    public void checkFeasibility(final FeasibilityCheckerProblem aProblem, final IFeasibilityCheckerCallback aCallback) {
//
//        /*
//         * Filter the edge case problems.
//         */
//        FeasibilityCheckerResult preprocessedResult = preprocess(aProblem);
//        if(preprocessedResult != null)
//        {
//            aCallback.onSuccess(aProblem, preprocessedResult);
//            return;
//        }
//
//        /*
//         * Transform feasibility checking instance to TAE object.
//         */
//        log.trace("Transforming feasibility checking instance to TAE object...");
//
//        //Write the instance file corresponding to the problem.
//        Map<Integer, Set<Integer>> domains = aProblem.getStationToDomainMapping();
//        Map<Integer,Integer> previousAssignment = aProblem.getPreviousAssignment();
//
//        String instanceName = domains.toString()+"_"+fStationConfigFolder;
//        String instanceHash;
//        MessageDigest aDigest = DigestUtils.getSha1Digest();
//        try {
//            byte[] aResult = aDigest.digest(instanceName.getBytes("UTF-8"));
//            instanceHash = new String(Hex.encodeHex(aResult));
//        }
//        catch (UnsupportedEncodingException e) {
//            throw new IllegalStateException("Could not hash instance string.", e);
//        }
//
//        StringBuilder instanceBuilder = new StringBuilder();
//        instanceBuilder.append(fStationConfigFolder);
//        instanceBuilder.append("_");
//
//        Iterator<Integer> stationIDIterator = domains.keySet().iterator();
//        while(stationIDIterator.hasNext())
//        {
//            int stationID = stationIDIterator.next();
//
//            Integer previousChannel = previousAssignment.get(stationID);
//            previousChannel = previousChannel != null ? previousChannel : -1;
//
//            instanceBuilder.append(stationID+";"+previousChannel+";"+StringUtils.join(domains.get(stationID),","));
//
//            if(stationIDIterator.hasNext())
//            {
//                instanceBuilder.append("_");
//            }
//        }
//
//        String instanceString = instanceBuilder.toString();
//
//        double cutoff = fCutoff * aProblem.getCutOffRatio();
//
//        log.trace("Starting feasibility checker with cutoff {}", cutoff);
//
//        final AlgorithmRunConfiguration config = new AlgorithmRunConfiguration(
//                new ProblemInstanceSeedPair(new ProblemInstance(instanceHash,instanceString), fSeed),
//                cutoff,
//                ParameterConfigurationSpace.getSingletonConfigurationSpace().getDefaultConfiguration(),
//                fAlgoExecConfig
//                );
//
//        /*
//         * Construct callbacks.
//         */
//
//        TargetAlgorithmEvaluatorCallback callback = new TargetAlgorithmEvaluatorCallback() {
//
//            private static final int MAX_FAILED_ATTEMPS = 10;
//            private AtomicInteger fFailureCount = new AtomicInteger(0);
//
//            @Override
//            public void onSuccess(List<AlgorithmRunResult> runs) {
//
//                log.trace("Received a successful run.");
//                log.trace("Instance name: {}", runs.get(0).getProblemInstance().getInstanceName());
//
//                if(runs.size()>1)
//                {
//                    throw new IllegalArgumentException("Only one run is submitted per feasibility check call.");
//                }
//
//                log.trace("Transforming run results back to feasibility checking objects...");
//
//                for(AlgorithmRunResult result : runs)
//                {
//
//                    SATResult satResult = SATResult.fromRunResult(result.getRunStatus());
//
//                    Map<Integer,Integer> assignment = new HashMap<Integer,Integer>();
//
//                    if(satResult.equals(SATResult.SAT))
//                    {
//                        String additionalRunData = result.getAdditionalRunData();
//                        String[] assignmentParts = additionalRunData.split(";");
//                        for(String assignmentPart : assignmentParts)
//                        {
//                            String[] stationParts = assignmentPart.split("=");
//                            try
//                            {
//                                int stationID = Integer.valueOf(stationParts[0]);
//                                int channel = Integer.valueOf(stationParts[1]);
//
//                                assignment.put(stationID, channel);
//                            }
//                            catch(NumberFormatException e)
//                            {
//                                throw new IllegalStateException("Could not parse assignment \""+additionalRunData+"\" returned from TAE for result "+result+".");
//                            }
//                        }
//                    }
//
//                    FeasibilityCheckerResult fcresult = new FeasibilityCheckerResult(satResult, assignment);
//
//                    log.trace("Triggering feasibility checking callback...");
//
//                    aCallback.onSuccess(aProblem, fcresult);
//                }
//            }
//
//            @Override
//            public void onFailure(RuntimeException e) {
//                int failurecount = fFailureCount.incrementAndGet();
//                log.error("Feasibility checker TAE caught an on failure exception.",e);
//                if(failurecount>=MAX_FAILED_ATTEMPS)
//                {
//                    log.error("Failed to complete run config {} after {} attempts.",config,MAX_FAILED_ATTEMPS);
//                    aCallback.onFailure(aProblem, e);
//
//                }
//                else
//                {
//                    log.info("Resubmitting the run ({}/{}).",failurecount,MAX_FAILED_ATTEMPS);
//                    fTAE.evaluateRunsAsync(config, this);
//                }
//            }
//        };
//
//        /*
//         * Submit run.
//         */
//        log.trace("Submitting instance to TAE...");
//
//        fTAE.evaluateRunsAsync(Arrays.asList(config), callback);
//
//        log.trace("...done!");
//
//    }
//
//    @Override
//    public void close() throws Exception {
//        fTAE.notifyShutdown();
//    }
//
//}
