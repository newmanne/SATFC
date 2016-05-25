package ca.ubc.cs.beta.fcc.simulator.station;

import ca.ubc.cs.beta.fcc.simulator.utils.SimulatorUtils;
import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.Map;

/**
 * Created by newmanne on 2016-05-20.
 */
@Slf4j
public class CSVStationDB implements StationDB {

    private final Map<Integer, StationInfo> data;

    public CSVStationDB(String infoFile) {
        final ImmutableMap.Builder<Integer, StationInfo> builder = ImmutableMap.builder();
        final Iterable<CSVRecord> records = SimulatorUtils.readCSV(infoFile);
        for (CSVRecord record : records) {
            final int id = Integer.parseInt(record.get("FacID"));
            final int volume = Integer.parseInt(record.get("Volume"));
            final String valueString = record.get("Value");
            Double value = null;
            if (!valueString.isEmpty()) {
                value = Double.parseDouble(valueString) * 10e6;
            }
            final Nationality nationality = Nationality.valueOf(record.get("Country"));
            final int channel = Integer.parseInt(record.get("Channel"));
            final StationInfo stationInfo = new StationInfo(id, volume, value, nationality, channel);
            if (!StationPackingUtils.UHF_CHANNELS.contains(channel)) {
                log.warn("Skipping station {} because it is not UHF", stationInfo);
                continue;
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
