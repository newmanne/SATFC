//package ca.ubc.cs.beta.ladderauctionsimulator.feasibilitychecker;
//
//
///**
// * Interface to a station repacking feasibility checker.
// * @author afrechet
// */
//public interface IFeasibilityChecker extends AutoCloseable{
//
//	/**
//	 * Asynchronously solve a feasibility checking problem. The provided callback is given to the (possibly different) thread
//	 * doing the solving, and is executed when finished with the problem.
//	 * @param aProblem - a feasibility checking problem.
//	 * @param aCallback - the callback to execute on termination.
//	 */
//	public void checkFeasibility(FeasibilityCheckerProblem aProblem, IFeasibilityCheckerCallback aCallback);
//}
