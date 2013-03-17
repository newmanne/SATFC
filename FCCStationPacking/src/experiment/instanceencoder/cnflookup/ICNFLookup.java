package experiment.instanceencoder.cnflookup;

import java.util.Set;

import data.Station;

public interface ICNFLookup {
	
	public boolean hasCNFfor(Set<Station> aStations);
	
	public String getCNFfor(Set<Station> aStations) throws Exception;
	
	public String getCNFNamefor(Set<Station> aStations);
	
	public void addCNFfor(Set<Station> aStations, String aCNFFileName) throws Exception;
	
	public String addCNFfor(Set<Station> aStations) throws Exception;

}
