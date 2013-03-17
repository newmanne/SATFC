package experiment.instanceencoder.cnfencoder;

import java.util.Set;

import data.Station;

public interface ICNFEncoder {

	public String encode(Set<Station> aStations);
	
}
