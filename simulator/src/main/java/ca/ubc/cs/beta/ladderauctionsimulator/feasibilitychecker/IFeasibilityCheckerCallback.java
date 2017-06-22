//package ca.ubc.cs.beta.ladderauctionsimulator.feasibilitychecker;
//
//
///**
// * Single function callback that is used at the end of a feasibility checking run.
// * @author afrechet
// */
//public interface IFeasibilityCheckerCallback {
//
//	/**
//	 * Method executed when the feasibility checker ends to process a successful outcome.
//	 * @param aProblem - a feasibility checking problem.
//	 * @param aResult - the result obtained from solving the given feasibility checking problem.
//	 */
//	public void onSuccess(FeasibilityCheckerProblem aProblem, FeasibilityCheckerResult aResult);
//
//	/**
//	 * Method executed when the feasibility checker ends with a runtime exception.
//	 * @param aProblem - a feasibility checking problem.
//	 * @param aException - the exception caught while trying to solve the given feasibility checking problem.
//	 */
//	public void onFailure(FeasibilityCheckerProblem aProblem, RuntimeException aException);
//}
