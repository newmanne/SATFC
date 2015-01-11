package ca.ubc.cs.beta.stationpacking.facade;

import ca.ubc.cs.beta.stationpacking.database.CachingDecoratorFactory;
import ca.ubc.cs.beta.stationpacking.execution.parameters.SATFCCachingParameters;
import lombok.Data;
import lombok.experimental.Builder;

@Data
public class SATFCFacadeParameter {
	
	private final String claspLibrary;
	private final boolean initializeLogging;
	private final String cNFDirectory;
	private final String resultFile;
	private final SolverChoice solverChoice;
	private final SolverCustomizationOptions options;
	private final boolean cache;
	private final CachingDecoratorFactory cachingDecoratorFactory;
	private final String interference;

	public static enum SolverChoice
	{
		SATFC,
		MIPFC;
	}

	@Data
	public static class SolverCustomizationOptions {
		private boolean presolve = true;
		private boolean underconstrained = true;
		private boolean decompose = true;
	}

}

