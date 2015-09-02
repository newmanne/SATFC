/**
 * Copyright 2015, Auctionomics, Alexandre Fréchette, Neil Newman, Kevin Leyton-Brown.
 *
 * This file is part of SATFC.
 *
 * SATFC is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SATFC is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SATFC.  If not, see <http://www.gnu.org/licenses/>.
 *
 * For questions, contact us at:
 * afrechet@cs.ubc.ca
 */
package ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles;

import ca.ubc.cs.beta.stationpacking.facade.datamanager.data.ManagerBundle;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.PythonAssignmentVerifierDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.ConstraintGrouper;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.IComponentGrouper;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.AssignmentVerifierDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.ConnectedComponentGroupingDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.mip.MIPBasedSolver;

/**
 * SATFC solver bundle that lines up pre-solving and main solver.
 *
 * @author afrechet
 */
public class MIPFCSolverBundle extends ASolverBundle {

    private static Logger log = LoggerFactory.getLogger(MIPFCSolverBundle.class);

    private final ISolver fSolver;

    /**
     * Create a SATFC solver bundle.
     *
     * @param dataBundle         - manager bundle that contains station manager and constraint manager.
     */
    public MIPFCSolverBundle(
            ManagerBundle dataBundle,
            boolean presolve,
            boolean decompose
    		) {

        super(dataBundle);

        log.debug("MIPFC solver bundle.");


        log.debug("Decomposing intances into connected components using constraint graph.");
        IComponentGrouper aGrouper = new ConstraintGrouper();

        log.debug("Initializing base MIP solvers.");
        ISolver solver = new MIPBasedSolver(getConstraintManager());


        /**
         * Decorate solvers - remember that the decorator that you put first is applied last
         */

        if (decompose) 
        {
        	// Split into components
            log.debug("Decorate solver to split the graph into connected components and then merge the results");
            solver = new ConnectedComponentGroupingDecorator(solver, aGrouper, getConstraintManager());
        }

        //Remove unconstrained stations.
//        log.debug("Decorate solver to first remove underconstrained stations.");
//        solver = new UnderconstrainedStationRemoverSolverDecorator(solver, getConstraintManager());

        //Verify results.
        /* 
         * NOTE: this is a MANDATORY decorator, and any decorator placed below this must not alter the answer or the assignment returned.
         */
        solver = new PythonAssignmentVerifierDecorator(solver, getInterferenceFolder(), getCompact());
        solver = new AssignmentVerifierDecorator(solver, getConstraintManager(), getStationManager());

        fSolver = solver;

    }

    @Override
    public ISolver getSolver(StationPackingInstance aInstance) {

        //Return the right solver based on the channels in the instance.
    	return fSolver;
    }

    @Override
    public void close() {
        fSolver.notifyShutdown();
    }


}
