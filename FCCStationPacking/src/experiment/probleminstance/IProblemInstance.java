package experiment.probleminstance;

import java.util.ArrayList;

/**
 * A station packing problem instance container object.
 * @author afrechet
 *
 */
public interface IProblemInstance {
	
	@Override
	public String toString();
	
	/**
	 * @return The CNF file names associated with the problem instance.
	 */
	public ArrayList<String> getCNFs();
	
	
	
}
