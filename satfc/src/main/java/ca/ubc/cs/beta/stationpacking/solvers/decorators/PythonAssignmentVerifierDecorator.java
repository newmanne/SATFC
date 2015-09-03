package ca.ubc.cs.beta.stationpacking.solvers.decorators;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.data.DataManager;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import lombok.extern.slf4j.Slf4j;
import org.python.core.PyObject;
import org.python.util.PythonInterpreter;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;

import java.io.File;
import java.util.List;

/**
 * Created by emily404 on 8/31/15.
 */
@Slf4j
public class PythonAssignmentVerifierDecorator extends ASolverDecorator {

    private final PythonInterpreter python;

    /**
     * @param aSolver - decorated ISolver, verifying assignemnt in python.
     */
    public PythonAssignmentVerifierDecorator(ISolver aSolver, String interferenceFolder, boolean compact) {

        super(aSolver);
        log.debug("Initializing PythonAssignmentVerifierDecorator");

        python = new PythonInterpreter();
        python.execfile(getClass().getClassLoader().getResourceAsStream("verifier.py"));
        String interference = interferenceFolder + File.separator + DataManager.INTERFERENCES_FILE;
        String domain = interferenceFolder + "/" + DataManager.DOMAIN_FILE;

        String interferenceResult;
        if(compact){
            final String compactEvalString = "load_compact_interference(\"" + interference + "\")";
            final PyObject compactReturnObject = python.eval(compactEvalString);
            interferenceResult = compactReturnObject.toString();
        }else{
            final String nonCompactEvalString = "load_interference(\"" + interference + "\")";
            final PyObject nonCompactReturnObject = python.eval(nonCompactEvalString);
            interferenceResult = nonCompactReturnObject.toString();
        }
        final String domainEvalString = "load_domain_csv(\"" + domain + "\")";
        final PyObject loadDomainReturnObject = python.eval(domainEvalString);
        final String domainResult = loadDomainReturnObject.toString();

        if (interferenceResult.equals("0") && domainResult.equals("0")){
            log.debug("Interference loaded");
        } else {
            throw new IllegalStateException("Interference not loaded properly");
        }

    }

    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {

        final SolverResult result = fDecoratedSolver.solve(aInstance, aTerminationCriterion, aSeed);
        if (result.getResult().equals(SATResult.SAT)) {
            log.debug("Independently verifying the veracity of returned assignment using python verifier script");

            final String evalString = "check_violations(\'"+JSONUtils.toString(result.getAssignment())+"\')";
            final PyObject eval = python.eval(evalString);
            final String checkResult = eval.toString();
            if(!checkResult.equals("None")){
                List<String> violationResult = (List<String>) eval.__tojava__(List.class);
                throw new IllegalStateException("Station " + violationResult.get(0) + " is assigned to channel " + violationResult.get(1) + " which violated " + violationResult.get(2));
            };
            log.debug("Assignment was independently verified to be satisfiable.");
        }
        return result;
    }

}
