/**
 * Copyright 2014, Auctionomics, Alexandre Fréchette, Kevin Leyton-Brown.
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
package ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.sequential;

import java.util.ArrayList;
import java.util.List;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.ASolverBundle;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.ISolverBundle;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.composites.SequentialSolversComposite;

/**
 * Executes solver bundles in sequence to solve a single instance.
 * @author afrechet
 */
public class SequentialSolverBundle extends ASolverBundle {

	private final List<ISolverBundle> fSolverBundles;
	
    /**
     * @param aStationManager - station manager to create instances.
     * @param aConstraintManager - constraint manager to create instances.
     * @param aSolverBundles - the list of solver bundles to execute.
     */
	public SequentialSolverBundle(IStationManager aStationManager, IConstraintManager aConstraintManager, List<ISolverBundle> aSolverBundles)
	{
		super(aStationManager,aConstraintManager);
		
		fSolverBundles = aSolverBundles;
	}
	
	@Override
	public ISolver getSolver(StationPackingInstance aInstance) {
		List<ISolver> solvers = new ArrayList<ISolver>();
		
		for(ISolverBundle bundle : fSolverBundles)
		{
			solvers.add(bundle.getSolver(aInstance));
		}
		
		return new SequentialSolversComposite(solvers);
	}

	@Override
	public void close() throws Exception
	{
		for(ISolverBundle bundle : fSolverBundles)
		{
			bundle.close();
		}
	}

}
