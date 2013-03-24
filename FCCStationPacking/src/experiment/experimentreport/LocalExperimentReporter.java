package experiment.experimentreport;

import java.io.File;

import org.apache.commons.io.FileUtils;

import experiment.instance.IInstance;
import experiment.solver.result.SolverResult;




public class LocalExperimentReporter implements IExperimentReporter{

	private String fReportDirectory;
	private String fExperimentName;
	
	public LocalExperimentReporter(String aReportDirectory, String aExperimentName)
	{
		fReportDirectory = aReportDirectory;
		fExperimentName = aExperimentName;
	}
	
	@Override
	public void report(IInstance aInstance, SolverResult aRunResult) throws Exception{
		
		String aLine = aInstance.toString()+","+Double.toString(aRunResult.getRuntime())+","+aRunResult.getResult().toString()+"\n";
		
		FileUtils.writeStringToFile(new File(fReportDirectory+File.separatorChar+fExperimentName+".csv"),aLine, true);
		
	}

}
