package ca.ubc.cs.beta.fcc.simulator.station;

import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.fcc.simulator.utils.BandHelper;
import ca.ubc.cs.beta.fcc.simulator.utils.SimulatorUtils;
import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVRecord;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Created by newmanne on 2016-05-20.
 */
@Slf4j
public class CSVStationDB implements StationDB {

    private final Map<Integer, IStationInfo> data;

    public CSVStationDB(String infoFile, String volumeFile, IStationManager stationManager, int highest, boolean uhfOnly, Predicate<IStationInfo> ignoreFunction, Function<IStationInfo, IStationInfo> decorate) {
        log.info("Reading volumes from {}", volumeFile);
        // Parse volumes
        final ImmutableMap.Builder<Integer, Double> volumeBuilder = ImmutableMap.builder();
        final Iterable<CSVRecord> volumeRecords = SimulatorUtils.readCSV(volumeFile);
        for (CSVRecord record : volumeRecords) {
            int id = Integer.parseInt(record.get("FacID"));
            double volume = Double.parseDouble(record.get("Volume"));
            volumeBuilder.put(id, volume);
        }
        final ImmutableMap<Integer, Double> volumes = volumeBuilder.build();
        log.info("Finished reading volumes");

        final ImmutableMap.Builder<Integer, IStationInfo> builder = ImmutableMap.builder();
        log.info("Reading station info from {}", infoFile);
        final Iterable<CSVRecord> records = SimulatorUtils.readCSV(infoFile);
        for (CSVRecord record : records) {
            IStationInfo stationInfo;
            final int id = Integer.parseInt(record.get("FacID"));
            if (uhfOnly && !stationManager.getDomain(new Station(id)).stream().anyMatch(c -> c >= StationPackingUtils.UHFmin)) {
                log.warn("Skipping station {} because it is not UHF", id);
                continue;
            }
            final Nationality nationality = Nationality.valueOf(record.get("Country"));
            // Account for Canadian stations having a highest of 1 lower than the clearing target
            int stationHighest = nationality.equals(Nationality.US) ? highest : highest - 1;
            final ImmutableSet<Integer> domain = ImmutableSet.copyOf(stationManager.getRestrictedDomain(new Station(id), stationHighest, uhfOnly));
            final int channel = Integer.parseInt(record.get("Channel"));
            if (nationality.equals(Nationality.CA)) {
                // Canadian stations have some channel 52 stations... messes everything up...
                stationInfo = StationInfo.canadianStation(id, channel >= 14 ? Band.UHF : BandHelper.toBand(channel), domain);
            } else {
                final Band band = BandHelper.toBand(channel);
                final Map<Band, Double> values = new HashMap<>();
                final Map<String, String> recordAsMap = record.toMap();
                for (Band b : Band.values()) {
                    if (recordAsMap.containsKey(b.toString() + "Value")) {
                        values.put(b, Double.parseDouble(recordAsMap.get(b.toString() + "Value")) * 1e6);
                    }
                }
                if (values.isEmpty()) {
                    // Backwards compatibility
                    final String valueString = record.get("Value");
                    Preconditions.checkState(!valueString.isEmpty());
                    double value = Double.parseDouble(valueString) * 1e6;
                    values.put(band, value);
                }
                Preconditions.checkState(values.size() > 0);
                values.put(Band.OFF, 0.);
                Double volume = volumes.get(id);
                Preconditions.checkState(volume != null, "No volume for station %s", id);
                stationInfo = new StationInfo(id, volume, values, nationality, band, domain);
            }
            stationInfo = decorate.apply(stationInfo);
            if (!ignoreFunction.test(stationInfo)) {
                builder.put(id, stationInfo);
            }
        }
        data = builder.build();
    }

    public CSVStationDB(String infoFile, String volumeFile, IStationManager stationManager, int highest, boolean uhfOnly) {
        this(infoFile, volumeFile, stationManager, highest, uhfOnly, x -> false, x -> x);
    }

    @Override
    public IStationInfo getStationById(int stationID) {
        return data.get(stationID);
    }

    @Override
    public Collection<IStationInfo> getStations() {
        return data.values();
    }

}
