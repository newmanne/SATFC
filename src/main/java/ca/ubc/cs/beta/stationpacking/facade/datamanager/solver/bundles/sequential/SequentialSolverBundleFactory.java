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

import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.ISolverBundle;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.ISolverBundleFactory;

/**
 * Creates a sequential solver bundle from a list of solver bundle factories.
 * @author afrechet
 */
public class SequentialSolverBundleFactory implements ISolverBundleFactory{

	private final List<ISolverBundleFactory> fSolverBundleFactories;
	
	/**
	 * @param aSolverBundleFactories - the list of solver bundle factories to execute in line. 
	 */
	public SequentialSolverBundleFactory(List<ISolverBundleFactory> aSolverBundleFactories)
	{
		fSolverBundleFactories = aSolverBundleFactories;
	}
	
	@Override
	public ISolverBundle getBundle(IStationManager aStationManager,
			IConstraintManager aConstraintManager) {
		
		List<ISolverBundle> bundles = new ArrayList<ISolverBundle>();
		
		for(ISolverBundleFactory bundleFactory : fSolverBundleFactories)
		{
			bundles.add(bundleFactory.getBundle(aStationManager, aConstraintManager));
		}
		
		return new SequentialSolverBundle(aStationManager, aConstraintManager, bundles);
		
	}



}
