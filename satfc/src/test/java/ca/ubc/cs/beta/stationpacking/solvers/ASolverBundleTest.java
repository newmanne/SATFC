package ca.ubc.cs.beta.stationpacking.solvers;

import java.io.FileNotFoundException;

import org.junit.Assert;
import org.junit.Test;

import ca.ubc.cs.beta.stationpacking.StationPackingTestUtils;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.ChannelSpecificConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.DomainStationManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.facade.SATFCFacadeBuilder;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.ISolverBundle;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.SATFCParallelSolverBundle;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.termination.cputime.CPUTimeTerminationCriterion;

import com.google.common.io.Resources;

/**
 * Created by newmanne on 22/05/15.
 */
public abstract class ASolverBundleTest {

    protected IStationManager stationManager;
    protected IConstraintManager manager;

    public ASolverBundleTest() throws FileNotFoundException {
        stationManager = new DomainStationManager(Resources.getResource("data/021814SC3M/Domain.csv").getFile());
        manager = new ChannelSpecificConstraintManager(stationManager, Resources.getResource("data/021814SC3M/Interference_Paired.csv").getFile());
    }

    protected abstract ISolverBundle getBundle();

    @Test
    public void testSimplestProblemPossible() {
        ISolverBundle bundle = getBundle();
        final StationPackingInstance instance = StationPackingTestUtils.getSimpleInstance();
        final SolverResult solve = bundle.getSolver(instance).solve(instance, new CPUTimeTerminationCriterion(60.0), 1);
        Assert.assertEquals(StationPackingTestUtils.getSimpleInstanceAnswer(), solve.getAssignment()); // There is only one answer to this problem
    }

    public static class CacheOnlySolverBundleTest extends ASolverBundleTest {

        public CacheOnlySolverBundleTest() throws FileNotFoundException {
        }

        @Override
        protected ISolverBundle getBundle() {
            return new SATFCParallelSolverBundle(
                    SATFCFacadeBuilder.findSATFCLibrary(),
                    stationManager,
                    manager,
                    null,
                    true,
                    true,
                    true,
                    "http://localhost:8080/satfcserver"
            );
//            return new CacheOnlySolverBundle(stationManager, manager, "http://localhost:8080/satfcserver", false);
        }

    }

}
