//package ca.ubc.cs.beta.ladderauctionsimulator.feasibilitychecker.callbacks;
//
//import java.util.concurrent.Semaphore;
//import java.util.concurrent.atomic.AtomicReference;
//
//import ca.ubc.cs.beta.ladderauctionsimulator.feasibilitychecker.FeasibilityCheckerProblem;
//import ca.ubc.cs.beta.ladderauctionsimulator.feasibilitychecker.FeasibilityCheckerResult;
//import ca.ubc.cs.beta.ladderauctionsimulator.feasibilitychecker.IFeasibilityCheckerCallback;
//
///**
// * A feasiblity checker callback that has a notifier method that blocks until the decorated callback has fired.
// * @author afrechet
// */
//public class TerminationNotifierFeasibilityCheckerCallbackDecorator implements IFeasibilityCheckerCallback {
//
//	private final IFeasibilityCheckerCallback fDecoratedCallback;
//	private final Semaphore fSemaphore = new Semaphore(0);
//	private final AtomicReference<RuntimeException> fException = new AtomicReference<RuntimeException>();
//
//	public TerminationNotifierFeasibilityCheckerCallbackDecorator(IFeasibilityCheckerCallback aCallback)
//	{
//		fDecoratedCallback = aCallback;
//	}
//
//	@Override
//	public void onSuccess(FeasibilityCheckerProblem aProblem,
//			FeasibilityCheckerResult aResult) {
//
//		fDecoratedCallback.onSuccess(aProblem, aResult);
//		fSemaphore.release();
//
//	}
//
//	@Override
//	public void onFailure(FeasibilityCheckerProblem aProblem,
//			RuntimeException aException) {
//		fException.set(aException);
//		fDecoratedCallback.onFailure(aProblem, aException);
//		fSemaphore.release();
//	}
//
//	/**
//	 * Blocks until either the onSuccess() or onFailure() methods have been called (at least once). If the onFailure() method was called,
//	 * then the encountered runtime exception is also thrown on awaitCallback().
//	 */
//	public void awaitCallback()
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
//
//			return;
//		}
//		finally
//		{
//			fSemaphore.release();
//		}
//	}
//
//}
