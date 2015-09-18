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

import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.data.DataManager;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.data.ManagerBundle;
import lombok.extern.slf4j.Slf4j;
import org.python.core.PyObject;
import org.python.util.PythonInterpreter;

import java.io.File;

/**
 * Abstract solver bundles that handles data management.
 * @author afrechet
 */
@Slf4j
public abstract class ASolverBundle implements ISolverBundle{

	private final IStationManager fStationManager;
	private final IConstraintManager fConstraintManager;
	private final String fInterferenceFolder;
	private final boolean fCompact;

	/**
	 * Create an abstract solver bundle with the given data management objects.
	 *  @param dataBundle manager bundle that contains station manager and constraint manager.
	 */
	public ASolverBundle(ManagerBundle dataBundle)
	{
		fStationManager = dataBundle.getStationManager();
		fConstraintManager = dataBundle.getConstraintManager();
		fInterferenceFolder = dataBundle.getInterferenceFolder();
		fCompact = dataBundle.getCompact();
	}
	
	@Override
	public IStationManager getStationManager()
	{
		return fStationManager;
	}
	
	@Override
	public IConstraintManager getConstraintManager()
	{
		return fConstraintManager;
	}

	@Override
	public String getInterferenceFolder()
	{
		return fInterferenceFolder;
	}

	@Override
	public boolean getCompact()
	{
		return fCompact;
	}

	@Override
	public PythonInterpreter getPythonInterpreter() {

		PythonInterpreter python;

		python = new PythonInterpreter();
		python.execfile(getClass().getClassLoader().getResourceAsStream("verifier.py"));
		String interference = getInterferenceFolder() + File.separator + DataManager.INTERFERENCES_FILE;
		String domain = getInterferenceFolder() + "/" + DataManager.DOMAIN_FILE;

		String interferenceResult;
		if(getCompact()){
			final String compactEvalString = "load_compact_interference(\"" + interference + "\")";
			log.debug("Evaluation string feed to Python interpreter: " + compactEvalString);

			final PyObject compactReturnObject = python.eval(compactEvalString);
			interferenceResult = compactReturnObject.toString();
		}else{
			final String nonCompactEvalString = "load_interference(\"" + interference + "\")";
			log.debug("Evaluation string feed to Python interpreter: " + nonCompactEvalString);

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

		return python;
	}

}
