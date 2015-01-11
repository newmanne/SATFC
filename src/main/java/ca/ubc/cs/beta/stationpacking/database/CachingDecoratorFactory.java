package ca.ubc.cs.beta.stationpacking.database;

import ca.ubc.cs.beta.stationpacking.solvers.ISolver;

public interface CachingDecoratorFactory {
	
	ISolver createCachingDecorator(ISolver aSolver, String aIntereference);

}
