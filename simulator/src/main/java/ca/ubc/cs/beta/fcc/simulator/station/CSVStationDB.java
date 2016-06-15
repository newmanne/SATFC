package ca.ubc.cs.beta.fcc.simulator.station;

import ca.ubc.cs.beta.fcc.simulator.utils.SimulatorUtils;
import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVRecord;

import java.util.Collection;
import java.util.Map;

/**
 * Created by newmanne on 2016-05-20.
 */
@Slf4j
public class CSVStationDB implements StationDB {

    private final Map<Integer, StationInfo> data;

    public CSVStationDB(String infoFile, IStationManager stationManager, boolean ignoreCanada) {
        final ImmutableMap.Builder<Integer, StationInfo> builder = ImmutableMap.builder();
        final Iterable<CSVRecord> records = SimulatorUtils.readCSV(infoFile);
        for (CSVRecord record : records) {
            final StationInfo stationInfo;
            final int id = Integer.parseInt(record.get("FacID"));
            if (!stationManager.getDomain(new Station(id)).stream().anyMatch(c -> c >= StationPackingUtils.UHFmin)) {
                log.warn("Skipping station {} because it is not UHF", id);
                continue;
            }
            final Nationality nationality = Nationality.valueOf(record.get("Country"));
            if (nationality.equals(Nationality.CA)) {
                if (ignoreCanada) {
                    continue;
                }
                stationInfo = StationInfo.canadianStation(id);
            } else {
                final String valueString = record.get("Value");
                Preconditions.checkState(!valueString.isEmpty());
                double value = Double.parseDouble(valueString) * 1e6;
                // pandas stores as float64 if you have NA values...
                int volume = (int) Double.parseDouble(record.get("Volume"));
                stationInfo = new StationInfo(id, volume, value, nationality);
            }
            builder.put(id, stationInfo);
        }
        data = builder.build();
    }

    @Override
    public StationInfo getStationById(int stationID) {
        return data.get(stationID);
    }

    @Override
    public Collection<StationInfo> getStations() {
        return data.values();
    }

}
