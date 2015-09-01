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

import java.io.File;

/**
 * Created by emily404 on 8/31/15.
 */
@Slf4j
public class PythonAssignmentVerifierDecorator extends ASolverDecorator {

    private final String fConfigFolder;
    private final PythonInterpreter python;

    /**
     * @param aSolver - decorated ISolver.
     */
    public PythonAssignmentVerifierDecorator(ISolver aSolver, String configFolder) {

        super(aSolver);
        log.info("Initializing PythonAssignmentVerifierDEcorator");

        fConfigFolder = configFolder;
        python = new PythonInterpreter();
        python.execfile(getClass().getClassLoader().getResourceAsStream("verifier.py"));
        configFolder = "/ubc/cs/project/arrow/satfc/public/interference/021814SC3M";
        String interference = configFolder + File.separator + DataManager.INTERFERENCES_FILE;
        String domain = configFolder + "/" + DataManager.DOMAIN_FILE;
        final String es1 = "load_compact_interference(\"" + interference + "\")";
        final PyObject e1 = python.eval(es1);
        final String interferenceResult = e1.toString();
        final String es2 = "load_domain_csv(\"" + domain + "\")";
        final PyObject e2 = python.eval(es2);
        final String domainResult = e2.toString();

        log.info(interferenceResult + " " + domainResult);
        if (interferenceResult.equals("0") && domainResult.equals("0")){
            log.info("Interference loaded");
        } else {
            log.error("Interference not loaded properly");
        }

    }

    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
        log.info("in decorator solver");
        final SolverResult result = fDecoratedSolver.solve(aInstance, aTerminationCriterion, aSeed);
        if (result.getResult().equals(SATResult.SAT)) {
            log.info("Independently verifying the veracity of returned assignment using python verifier script");

            final String evalString = "check_violations(\"" + result + "\")";
            final PyObject eval = python.eval(evalString);
            final String checkResult = eval.toString();
            if (checkResult.equals("1")){
                throw new IllegalStateException("Found a violation");
            } else {
                log.info("this check passed");
            }

            log.info("Assignment was independently verified to be satisfiable.");
        }
        return result;
    }

}
