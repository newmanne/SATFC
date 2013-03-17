package experiment.instanceencoder;


import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;


import data.Station;
import experiment.instanceencoder.cnfencoder.ICNFEncoder;
import experiment.instanceencoder.cnflookup.ICNFLookup;
import experiment.instanceencoder.componentgrouper.IComponentGrouper;
import experiment.probleminstance.IProblemInstance;
import experiment.probleminstance.ProblemInstance;

public class InstanceEncoder implements IInstanceEncoder {

	private ICNFLookup fCNFLookup;
	private ICNFEncoder fCNFEncoder;
	private IComponentGrouper fComponentGrouper;
	private String fCNFDirectory;
	
	public InstanceEncoder(ICNFEncoder aCNFEncoder, ICNFLookup aCNFLookup, IComponentGrouper aComponentGrouper, String aCNFDirectory)
	{
		fCNFLookup = aCNFLookup;
		fCNFEncoder = aCNFEncoder;
		fComponentGrouper = aComponentGrouper;
		fCNFDirectory = aCNFDirectory;
		
	}
	
	@Override
	public IProblemInstance getProblemInstance(Set<Station> aStations) throws Exception {
		
		Map<Set<Station>,String> aStationComponenttoCNF = new HashMap<Set<Station>,String>();
		
		System.out.println("Getting groups...");
		ArrayList<HashSet<Station>> aComponentGroups = fComponentGrouper.group(aStations);
		System.out.println("There are "+aComponentGroups.size()+" groups");
		
		for(HashSet<Station> aStationComponent : aComponentGroups)
		{
			
	
			String aCNFFileName;
			if(fCNFLookup.hasCNFfor(aStationComponent))
			{
				aCNFFileName = fCNFDirectory + File.separatorChar + fCNFLookup.getCNFfor(aStationComponent);
			}
			else
			{
				System.out.println("Encoding stations and finding new CNF...");
				String aCNF = fCNFEncoder.encode(aStationComponent);
				
				String aCNFName = fCNFLookup.addCNFfor(aStationComponent);
				
				aCNFFileName = fCNFDirectory + File.separatorChar + aCNFName;
				
				FileUtils.writeStringToFile(new File(aCNFFileName), aCNF);
		
			}
			
			aStationComponenttoCNF.put(aStationComponent, aCNFFileName);	
		
		}
		
		return new ProblemInstance(aStationComponenttoCNF);
	}

}
