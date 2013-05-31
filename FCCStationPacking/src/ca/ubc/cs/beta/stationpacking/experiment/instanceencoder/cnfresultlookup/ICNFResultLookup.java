package ca.ubc.cs.beta.stationpacking.experiment.instanceencoder.cnfresultlookup;


import java.io.IOException;

import ca.ubc.cs.beta.stationpacking.experiment.instance.IInstance;
import ca.ubc.cs.beta.stationpacking.experiment.solver.result.SolverResult;


/**
 * Records and looks up previously existing CNF instance names based on their corresponding set of stations.
 * Improves space management (fewer CNF files) and
 * speed (when executed on an identical previously solved CNF, certain solvers return previous run time).
 * @author afrechet, narnosti
 *
 */
public interface ICNFResultLookup {
	
	//NA - why are these all public? Which ones are actually called?
	
	/**
	 * @param aStations - a set of stations.
	 * @return True if it contains a CNF for given station set.
	 */
	public boolean hasSolverResult(IInstance aInstance);
	
	/**
	 * Get solver result for a given problem instance.
	 * @param aInstance - a problem instance.
	 * @return The solver result recorded for the problem instance.  
	 * @throws Exception Throws exception if instance not recorded.
	 */
	public SolverResult getSolverResult(IInstance aInstance) throws Exception;

	/**
	 * Save solver result for a given problem instance.
	 * @param aInstance - a problem instance.
	 * @param aResult - a solver result.
	 * @return true if already present, false otherwise.
	 */
	public boolean putSolverResult(IInstance aInstance, SolverResult aResult);
	
	/**
	 * @param aStations - a set of stations.
	 * @return CNF instance file name corresponding to input station set, if any.
	 * @throws Exception - if input station set has no corresponding CNF name.
	 */
	public String getCNFNameFor(IInstance aInstance);
	
	public void writeToFile() throws IOException;


}