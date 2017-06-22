//package ca.ubc.cs.beta.ladderauctionsimulator.feasibilitychecker.callbacks;
//
//import java.util.concurrent.Semaphore;
//import java.util.concurrent.atomic.AtomicReference;
//
//import org.apache.commons.math3.util.Pair;
//
//import ca.ubc.cs.beta.ladderauctionsimulator.feasibilitychecker.FeasibilityCheckerProblem;
//import ca.ubc.cs.beta.ladderauctionsimulator.feasibilitychecker.FeasibilityCheckerResult;
//import ca.ubc.cs.beta.ladderauctionsimulator.feasibilitychecker.IFeasibilityCheckerCallback;
//
///**
// * A feasibility checker callback that has a blocking method which returns the result of the execution.
// * @author afrechet
// */
//public class BlockingFeasibilityCheckerCallback implements IFeasibilityCheckerCallback{
//
//	final Semaphore fSemaphore = new Semaphore(0);
//
//	final AtomicReference<FeasibilityCheckerProblem> fProblem = new AtomicReference<FeasibilityCheckerProblem>();
//	final AtomicReference<FeasibilityCheckerResult> fResult = new AtomicReference<FeasibilityCheckerResult>();
//	final AtomicReference<RuntimeException> fException = new AtomicReference<RuntimeException>();
//
//	@Override
//	public void onSuccess(FeasibilityCheckerProblem aProblem,
//			FeasibilityCheckerResult aResult) {
//		fProblem.set(aProblem);
//		fResult.set(aResult);
//		fSemaphore.release();
//	}
//
//	@Override
//	public void onFailure(FeasibilityCheckerProblem aProblem,
//			RuntimeException aException){
//		fException.set(aException);
//		fSemaphore.release();
//
//	}
//
//	/**
//	 * Blocks until the execution is complete (either onSuccess or onFailure is called).
//	 * @return the result of the execution.
//	 */
//	public Pair<FeasibilityCheckerProblem,FeasibilityCheckerResult> getOutcome()
//	{
//		try
//		{
//			fSemaphore.acquireUninterruptibly();
//
//			RuntimeException e = fException.get();
//			if(e != null)
//			{
//				throw e;
//			}
//			return new Pair<FeasibilityCheckerProblem,FeasibilityCheckerResult>(fProblem.get(),fResult.get());
//		}
//		finally
//		{
//			fSemaphore.release();
//		}
//	}
//
//	public FeasibilityCheckerResult getResult() {
//		return getOutcome().getSecond();
//	}
//
//
//
//
//
//}
