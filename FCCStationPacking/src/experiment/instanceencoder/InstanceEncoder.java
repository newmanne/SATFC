package experiment.instanceencoder;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import data.Station;
import experiment.instance.IInstance;
import experiment.instance.Instance;
import experiment.instanceencoder.cnfencoder.ICNFEncoder;
import experiment.instanceencoder.cnflookup.ICNFLookup;
import experiment.instanceencoder.componentgrouper.IComponentGrouper;

public class InstanceEncoder implements IInstanceEncoder {
	
	private static Logger log = LoggerFactory.getLogger(InstanceEncoder.class);
	
	private ICNFLookup fCNFLookup;
	private ICNFEncoder fCNFEncoder;
	private IComponentGrouper fComponentGrouper;
	private String fCNFDirectory;
	
	/**
	 * Build a simple instance encoder. 
	 * @param aCNFEncoder 
	 * @param aCNFLookup
	 * @param aComponentGrouper
	 * @param aCNFDirectory
	 */
	public InstanceEncoder(ICNFEncoder aCNFEncoder, ICNFLookup aCNFLookup, IComponentGrouper aComponentGrouper, String aCNFDirectory)
	{
		fCNFLookup = aCNFLookup;
		fCNFEncoder = aCNFEncoder;
		fComponentGrouper = aComponentGrouper;
		fCNFDirectory = aCNFDirectory;
		
	}
	
	@Override
	public IInstance getProblemInstance(Set<Station> aStations) throws Exception {
		
		Map<Set<Station>,String> aStationComponenttoCNF = new HashMap<Set<Station>,String>();
		
		log.info("Grouping stations.");
		ArrayList<HashSet<Station>> aComponentGroups = fComponentGrouper.group(aStations);
		log.info("There are {} groups.",aComponentGroups.size());
	
	
		for(HashSet<Station> aStationComponent : aComponentGroups)
		{

			String aCNFFileName;
			if(fCNFLookup.hasCNFfor(aStationComponent))
			{
				aCNFFileName = fCNFDirectory + File.separatorChar + fCNFLookup.getCNFfor(aStationComponent);
			}
			else
			{
				log.info("Encoding stations and finding new CNF...");
				String aCNF = fCNFEncoder.encode(aStationComponent);
				
				String aCNFName = fCNFLookup.addCNFfor(aStationComponent);
				
				aCNFFileName = fCNFDirectory + File.separatorChar + aCNFName;
				
				FileUtils.writeStringToFile(new File(aCNFFileName), aCNF);
		
			}
			
			aStationComponenttoCNF.put(aStationComponent, aCNFFileName);	
		
		}

		
		return new Instance(aStationComponenttoCNF);
	}

}
