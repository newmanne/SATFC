package ca.ubc.cs.beta.stationpacking.execution;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.ubc.cs.beta.aclib.misc.jcommander.JCommanderHelper;
import ca.ubc.cs.beta.aclib.misc.options.UsageSection;
import ca.ubc.cs.beta.aclib.options.ConfigToLaTeX;
import ca.ubc.cs.beta.stationpacking.datastructures.Instance;
import ca.ubc.cs.beta.stationpacking.datastructures.SolverResult;
import ca.ubc.cs.beta.stationpacking.execution.parameters.ExecutableSolverParameters;
import ca.ubc.cs.beta.stationpacking.solver.ISolver;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

public class SingleInstanceStatelessSolverExecutor {

	private static Logger log = LoggerFactory.getLogger(SingleInstanceStatelessSolverExecutor.class);
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		
		String[] aPaxosTargetArgs = {
				"-STATIONS_FILE",
				"/ubc/cs/home/a/afrechet/arrow-space/workspace/FCCStationPackingExperimentDir/Data/stations2.csv",
				"-DOMAINS_FILE",
				"/ubc/cs/home/a/afrechet/arrow-space/workspace/FCCStationPackingExperimentDir/Data/NewDACData/Domain-041813.csv",
				"-CONSTRAINTS_FILE",
				"/ubc/cs/home/a/afrechet/arrow-space/workspace/FCCStationPackingExperimentDir/Data/NewDACData/Interferences-041813.csv",
				"-CNF_DIR",
				"/ubc/cs/home/a/afrechet/arrow-space/workspace/FCCStationPackingExperimentDir/CNFs",
				"-SOLVER",
				"tunedclasp",
				"--execDir",
				"SATsolvers",
				"--algoExec",
				"python solverwrapper.py",
				"--cutoffTime",
				"1800",
				"-CUTOFF",
				"5",
				"--logAllCallStrings",
				"true",
				"-PACKING_CHANNELS",
				"14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30",
				"-PACKING_STATIONS",
				//"25684,32334,39664",
				"144,146,147,148,363,413,414,416,593,713,714,717,999,1136,1151,1328,2245,2424,2708,2710,2728,2739,2770,3255,3369,3978,4152,4190,4297,4301,4328,4585,4624,4693,5360,5468,5800,5801,5981,5982,6096,6359,6554,6601,6823,6838,7143,7675,7780,7890,7893,7908,7933,8564,8617,8620,8688,9015,9088,9635,9719,9762,9766,9781,9881,9908,9987,10019,10203,10221,10267,10318,10758,10802,11125,11204,11265,11289,11371,11559,11908,11913,11951,12171,12279,12520,12522,12855,13058,13060,13200,13206,13456,13602,13607,13924,13950,13988,13991,13992,14040,16530,16788,16940,16950,17012,17433,17683,18252,18793,19183,19199,19200,19776,19777,20426,20624,20871,21536,21649,21729,21737,22161,22204,22211,22570,22644,22685,22819,23074,23342,23394,23428,23918,23937,23948,23960,24316,24436,24485,24508,24582,24783,25067,25456,25544,25683,25932,26304,26655,26950,27772,27969,28010,28119,28324,28462,28468,28476,29000,29102,29108,29114,29547,29712,30244,30577,31870,32142,32334,33543,33742,33770,34195,34200,34204,34329,34439,34457,34529,34874,34894,35037,35042,35092,35095,35336,35380,35385,35396,35417,35419,35460,35486,35576,35587,35630,35703,35705,35841,35843,35846,35852,35855,35867,35910,35920,35994,36504,36838,36846,36916,36917,36918,37005,37099,37102,37106,37179,37503,37511,38214,38562,39736,39746,40875,40993,41095,41223,41225,41230,41237,41315,41397,41398,42359,42636,42663,43169,43952,44052,46979,47535,47707,47903,47904,48477,48481,48525,48589,48608,48667,48975,49153,49235,49264,49330,49439,49632,50147,50198,50205,50590,50782,51102,51163,51349,51488,51499,51502,51517,51518,51569,51597,51969,52073,52527,52579,52887,52953,53113,53114,53116,53517,53586,53819,53859,53921,55083,55516,55528,55644,56523,56528,57221,57832,57840,57884,57945,58340,58552,58725,58795,58835,59438,59442,59443,59444,59988,60018,60165,60354,60539,60552,60560,60654,60683,60793,60820,60827,60830,60850,61003,61009,61010,61064,61251,61504,61573,62182,62207,62354,62388,62469,63154,63768,63840,63867,64017,64444,64547,64548,64550,64588,64592,64969,64971,64983,65395,65526,65666,65681,65686,65690,66172,66185,66221,66258,66358,66398,66469,66589,66781,66790,66804,66996,67000,67002,67022,67048,67485,67602,67781,67787,67802,67866,67868,67869,67893,67910,67950,68007,68540,68581,68834,68886,68889,69114,69124,69237,69273,69360,69531,69532,69571,69692,69733,69735,69880,69994,70021,70034,70041,70251,70309,70416,70419,70537,70815,70852,70900,71069,71070,71074,71078,71121,71127,71217,71278,71293,71425,71427,71428,71657,71725,72054,72076,72098,72106,72120,72123,72278,72618,72971,73042,73101,73130,73150,73152,73187,73188,73195,73207,73230,73238,73263,73354,73371,73692,73879,73901,73910,73940,73982,74094,74100,74173,74174,74192,74197,74215,74424,76324,77451,77480,78908,78915,81458,81508,81593,83180"
				};
		
		args = aPaxosTargetArgs;
		
		//Parse the command line arguments in a parameter object.
		ExecutableSolverParameters aExecutableSolverParameter = new ExecutableSolverParameters();
		JCommander aParameterParser = JCommanderHelper.getJCommander(aExecutableSolverParameter, aExecutableSolverParameter.SolverParameters.AvailableTAEOptions);
		try
		{
			aParameterParser.parse(args);
		}
		catch (ParameterException aParameterException)
		{
			List<UsageSection> sections = ConfigToLaTeX.getParameters(aExecutableSolverParameter,aExecutableSolverParameter.SolverParameters.AvailableTAEOptions);
			
			boolean showHiddenParameters = false;
			
			//A much nicer usage screen than JCommander's 
			ConfigToLaTeX.usage(sections, showHiddenParameters);
			
			log.error(aParameterException.getMessage());
			return;
		}
		
		ISolver aSolver = null;
		try
		{
			try 
			{
				
				aSolver = aExecutableSolverParameter.getSolver();
				Instance aInstance = aExecutableSolverParameter.getInstance();
				
				log.info("Solving instance of {}",aInstance.getInfo());
				
				SolverResult aResult = aSolver.solve(aInstance, aExecutableSolverParameter.ProblemInstanceParameters.Cutoff, aExecutableSolverParameter.ProblemInstanceParameters.Seed);
				
				log.info("Solved.");
				log.info("Result : {}",aResult);
								
				System.out.println("Result for feasibility checker: "+aResult.toParsebleString());
			} 
			catch (Exception e) 
			{
				e.printStackTrace();
			}
			
		}
		finally{
			aSolver.notifyShutdown();
		}
		
		
		
		

	}

}