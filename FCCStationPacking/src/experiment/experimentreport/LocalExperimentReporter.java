package experiment.experimentreport;

import java.io.File;

import org.apache.commons.io.FileUtils;

import experiment.probleminstance.IProblemInstance;
import experiment.solver.RunResult;




public class LocalExperimentReporter implements IExperimentReporter{

	private String fReportDirectory;
	private String fExperimentName;
	
	public LocalExperimentReporter(String aReportDirectory, String aExperimentName)
	{
		fReportDirectory = aReportDirectory;
		fExperimentName = aExperimentName;
	}
	
	@Override
	public boolean report(IProblemInstance aInstance, RunResult aRunResult) throws Exception{
		
		String aLine = aInstance.toString()+","+Double.toString(aRunResult.getRuntime())+","+aRunResult.getResult().toString()+"\n";
		
		FileUtils.writeStringToFile(new File(fReportDirectory+File.separatorChar+fExperimentName+".csv"),aLine, true);
		
		return true;
	}

}
