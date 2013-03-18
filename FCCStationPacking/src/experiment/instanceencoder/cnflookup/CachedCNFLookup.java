package experiment.instanceencoder.cnflookup;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

import data.Station;

public class CachedCNFLookup implements ICNFLookup{

	private HashMap<Set<Station>,String> fStationsHashtoCNFName;
	
	public CachedCNFLookup(String aMapFileName)
	{	
		
		
		fStationsHashtoCNFName = new HashMap<Set<Station>,String>();
	}
	
	public CachedCNFLookup()
	{
		fStationsHashtoCNFName = new HashMap<Set<Station>,String>();
	}
	
	
	
	@Override
	public boolean hasCNFfor(Set<Station> aStations) {
		return fStationsHashtoCNFName.containsKey(aStations);
	}

	@Override
	public String getCNFfor(Set<Station> aStations) throws Exception{
		
		if(!hasCNFfor(aStations))
		{
			throw new Exception("Tried to lookup a CNF for a set of stations that is unavailable.");
		}
		else
		{
			return fStationsHashtoCNFName.get(aStations);
		}
		
	}
	
	@Override
	public void addCNFfor(Set<Station> aStations, String aCNFFileName) throws Exception {
		if(fStationsHashtoCNFName.containsKey(aStations)){
			throw new Exception("Tried to update a CNF for a set of stations that is already present.");
		}
		else
		{
			fStationsHashtoCNFName.put(aStations, aCNFFileName);
		}
	}

	@Override
	public String getCNFNamefor(Set<Station> aStations) {
		return StringHash(Station.hashStationSet(aStations))+".cnf";
	}

	@Override
	public String addCNFfor(Set<Station> aStations) throws Exception {
		if(fStationsHashtoCNFName.containsKey(aStations)){
			throw new Exception("Tried to update a CNF for a set of stations that is already present.");
		}
		else
		{
			String aCNFFileName = getCNFNamefor(aStations);
			fStationsHashtoCNFName.put(aStations, aCNFFileName);
			return aCNFFileName;
		}
	}
	
	private String StringHash(String aString)
	{
		MessageDigest digest = DigestUtils.getSha1Digest();
	
		try {
		        byte[] aResult = digest.digest(aString.getBytes("UTF-8"));
		        String aResultString = new String(Hex.encodeHex(aResult));
		
		        return aResultString;
		}
		catch (UnsupportedEncodingException e) 
		{
		        throw new IllegalStateException("Could not encode filename", e);
		}
	}

}
