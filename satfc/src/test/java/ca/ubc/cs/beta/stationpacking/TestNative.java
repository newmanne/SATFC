package ca.ubc.cs.beta.stationpacking;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.ChannelSpecificConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.DomainStationManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.execution.Converter;
import ca.ubc.cs.beta.stationpacking.execution.parameters.solver.sat.ClaspLibSATSolverParameters;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.sat.base.CNF;
import ca.ubc.cs.beta.stationpacking.solvers.sat.base.Literal;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.ISATDecoder;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.ISATEncoder;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.SATCompressor;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.jnalibraries.Clasp3Library;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.jnalibraries.ClaspLibrary;
import com.google.common.collect.Lists;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.util.Pair;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by newmanne on 01/04/15.
 */
@Slf4j
public class TestNative {


    //        String cnf = "c\n" +
//                "p cnf 3 2\n" +
//                "1 -3 0\n" +
//                "2 3 -1 0\n";
//
//        cnf = "p cnf 4 8\n" +
//                " 1  2 -3 0\n" +
//                "-1 -2  3 0\n" +
//                " 2  3 -4 0\n" +
//                "-2 -3  4 0\n" +
//                " 1  3  4 0\n" +
//                "-1 -3 -4 0\n" +
//                "-1  2  4 0\n" +
//                " 1 -2 -4 0";

    private HashSet<Literal> parseAssignment(int[] assignment)
    {
        HashSet<Literal> set = new HashSet<Literal>();
        for (int i = 1; i < assignment[0]; i++)
        {
            int intLit = assignment[i];
            int var = Math.abs(intLit);
            boolean sign = intLit > 0;
            Literal aLit = new Literal(var, sign);
            set.add(aLit);
        }
        return set;
    }



    // 2469-2483_4310537143272356051_107.srpk //hard

    @Test
    public void test() throws Exception {
        String libraryPath = "/home/newmanne/research/satfc/satfc/src/dist/clasp/libjnaclasp-3/jna/libjnaclasp.so";
        final String domainStationFileName = "/home/newmanne/interferences/021814SC3M/Domain.csv";
        final String interferenceFileName = "/home/newmanne/interferences/021814SC3M/Interference_Paired.csv";
        final String instanceFileS = "/home/newmanne/arrow/afrechet/experiments/satfc-paper/instances/dec14-instances/srpks/";
        // load the library
        final Clasp3Library claspLibrary2 = (Clasp3Library) Native.loadLibrary(libraryPath, Clasp3Library.class);
        final IStationManager stationManager = new DomainStationManager(domainStationFileName);
        final IConstraintManager manager = new ChannelSpecificConstraintManager(stationManager, interferenceFileName);
        log.info("Done loading constraints and domains");
        ISATEncoder fSATEncoder = new SATCompressor(manager);
        List<String> list = Lists.newArrayList("2469-2483_738374684548899781_63.srpk","2469-2483_4035838900442478427_45.srpk");
        for (String problem : list) {
            final String instanceFile = instanceFileS + problem;
            Converter.StationPackingProblemSpecs specs = Converter.StationPackingProblemSpecs.fromStationRepackingInstance(instanceFile);
            final StationPackingInstance instance = new StationPackingInstance(specs.getDomains().entrySet().stream().collect(Collectors.toMap(e -> stationManager.getStationfromID(e.getKey()), e -> e.getValue())), specs.getPreviousAssignment().entrySet().stream().collect(Collectors.toMap(e -> stationManager.getStationfromID(e.getKey()), e-> e.getValue())));
            Pair<CNF, ISATDecoder> aEncoding = fSATEncoder.encode(instance);
            ISATDecoder decoder = aEncoding.getValue();
            CNF aCNF = aEncoding.getKey();
            log.info("Done loading problem");
            for (int i = 0; i < 2; i++) {
                // check if the configuration is valid.
                final Pointer pRef1 = claspLibrary2.initProblem("--seed=1 " + ClaspLibSATSolverParameters.UHF_CONFIG_04_15, aCNF.toDIMACS(null));
                claspLibrary2.solveProblem(pRef1, 60.0);
                int resultState = claspLibrary2.getResultState(pRef1);
                if (resultState == 1) {
                    log.info("Result retunred");
                    final IntByReference pRef = claspLibrary2.getResultAssignment(pRef1);
                    int size = pRef.getValue();
                    int[] assignment = pRef.getPointer().getIntArray(0, size);
                    HashMap<Long, Boolean> aLitteralChecker = new HashMap<Long, Boolean>();
                    Map<Integer, Set<Station>> aStationAssignment = new HashMap<Integer, Set<Station>>();
                    for (Literal aLiteral : parseAssignment(assignment)) {
                        boolean aSign = aLiteral.getSign();
                        long aVariable = aLiteral.getVariable();

                        //Do some quick verifications of the assignment.
                        if (aLitteralChecker.containsKey(aVariable)) {
                            log.warn("A variable was presenlst twice in a SAT assignment.");
                            if (!aLitteralChecker.get(aVariable).equals(aSign)) {
                                throw new IllegalStateException("SAT assignment from TAE wrapper assigns a variable to true AND false.");
                            }
                        } else {
                            aLitteralChecker.put(aVariable, aSign);
                        }

                        //If the litteral is positive, then we keep it as it is an assigned station to a channel.
                        if (aSign) {
                            Pair<Station, Integer> aStationChannelPair = decoder.decode(aVariable);
                            Station aStation = aStationChannelPair.getKey();
                            Integer aChannel = aStationChannelPair.getValue();

                            if (!instance.getStations().contains(aStation) || !instance.getDomains().get(aStation).contains(aChannel)) {
                                throw new IllegalStateException("A decoded station and channel from a component SAT assignment is not in that component's problem instance. (" + aStation + ", channel:" + aChannel + ")");
                            }

                            if (!aStationAssignment.containsKey(aChannel)) {
                                aStationAssignment.put(aChannel, new HashSet<Station>());
                            }
                            aStationAssignment.get(aChannel).add(aStation);
                        }
                    }
                    SolverResult solverResult = new SolverResult(SATResult.SAT, 1.0, aStationAssignment);
                    final boolean correct = manager.isSatisfyingAssignment(solverResult.getAssignment());
                    if (!correct) {
                        log.error("GAAHHAHAHA NOT CORRECT!");
                    } else {
                        log.info("Result is correct");
                    }
                    log.info(solverResult.toString());
                } else {
                    log.info("UNSAT?");
                }
                claspLibrary2.destroyProblem(pRef1);
            }
        }
    }
}
