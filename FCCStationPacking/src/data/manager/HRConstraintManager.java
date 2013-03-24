package data.manager;

import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.util.Pair;

import au.com.bytecode.opencsv.CSVReader;

import data.Constraint;
import data.Station;

public class HRConstraintManager implements IConstraintManager{

	Set<Constraint> fPairwiseConstraints;
	HashMap<Station,Set<Integer>> fStationDomains;
	
	
	public HRConstraintManager(String aAllowedChannelsFilename, String aPairwiseConstraintsFilename) throws IOException
	{
		fStationDomains = new HashMap<Station,Set<Integer>>();
		try(CSVReader aReader = new CSVReader(new FileReader(aAllowedChannelsFilename)))
		{
			//Skip header
			aReader.readNext();
			
			String[] aLine;
			while((aLine = aReader.readNext())!=null)
			{
				Integer aID = Integer.valueOf(aLine[1]);
				Station aStation = new Station(aID);
				HashSet<Integer> aChannelDomain = new HashSet<Integer>();
				for(int i=2;i<aLine.length;i++)
				{
					Integer aChannel = Integer.valueOf(aLine[i]);
					aChannelDomain.add(aChannel);
				}
				fStationDomains.put(aStation, aChannelDomain);
			}
		}
		
		
		fPairwiseConstraints = new HashSet<Constraint>();
		try(CSVReader aReader = new CSVReader(new FileReader(aPairwiseConstraintsFilename)))
		{
			//Skip header
			aReader.readNext();
			
			String[] aLine;
			while((aLine = aReader.readNext())!=null)
			{
				Integer aID1 = Integer.valueOf(aLine[0]);
				Integer aChannel1 = Integer.valueOf(aLine[1]);
				Integer aID2 = Integer.valueOf(aLine[2]);
				Integer aChannel2 = Integer.valueOf(aLine[3]);
				
				fPairwiseConstraints.add(new Constraint(new Pair<Station,Integer>(new Station(aID1),aChannel1),new Pair<Station,Integer>(new Station(aID2),aChannel2)));
				
			}
		}
		
	}
	
	@Override
	public Set<Constraint> getPairwiseConstraints() {
		return fPairwiseConstraints;
	}

	@Override
	public Map<Station, Set<Integer>> getStationDomains() {
		return fStationDomains;
	}

}
