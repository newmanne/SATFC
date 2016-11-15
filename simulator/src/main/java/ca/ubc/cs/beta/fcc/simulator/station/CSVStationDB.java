package ca.ubc.cs.beta.fcc.simulator.station;

import ca.ubc.cs.beta.fcc.simulator.parameters.SimulatorParameters;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.fcc.simulator.utils.BandHelper;
import ca.ubc.cs.beta.fcc.simulator.utils.SimulatorUtils;
import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVRecord;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Created by newmanne on 2016-05-20.
 */
@Slf4j
public class CSVStationDB implements IStationDB.IModifiableStationDB {

    private final Map<Integer, IStationInfo> data;

    public CSVStationDB(String infoFile, IStationManager stationManager) {
        data = new HashMap<>();
        log.info("Reading station info from {}", infoFile);
        final Iterable<CSVRecord> records = SimulatorUtils.readCSV(infoFile);
        for (CSVRecord record : records) {
            IStationInfo stationInfo;
            final int id = Integer.parseInt(record.get("FacID"));
            final Nationality nationality = Nationality.valueOf(record.get("Country"));
            final ImmutableSet<Integer> domain = ImmutableSet.copyOf(stationManager.getDomain(new Station(id)));
            final int channel = Integer.parseInt(record.get("Channel"));
            final String city = record.get("City");
            final String call = record.get("Call");
            final int pop = Integer.parseInt(record.get("Population"));
            if (nationality.equals(Nationality.CA)) {
                // Canadian stations have some channel 52 stations... messes everything up...
                stationInfo = StationInfo.canadianStation(id, channel >= 14 ? Band.UHF : BandHelper.toBand(channel), domain, city, call, pop);
            } else {
                final Band band = BandHelper.toBand(channel);
                stationInfo = new StationInfo(id, nationality, band, domain, city, call, pop);
            }
            data.put(id, stationInfo);
        }
        // ugly...
        final Set<StationInfo> americanStations = data.values().stream().filter(s -> s.getNationality().equals(Nationality.US)).map(s -> (StationInfo) s).collect(Collectors.toSet());
        log.info("Finished reading stations");
        final Map<Band, List<IStationInfo>> collect = getStations().stream().collect(Collectors.groupingBy(IStationInfo::getHomeBand));
        for (Band band : collect.keySet()) {
            log.info("{} {} stations", collect.get(band).size(), band);
        }
    }

    @Override
    public IStationInfo getStationById(int stationID) {
        return data.get(stationID);
    }

    @Override
    public Collection<IStationInfo> getStations() {
        return data.values();
    }

    @Override
    public void removeStation(int stationID) {
        final IStationInfo removed = data.remove(stationID);
        Preconditions.checkNotNull(removed, "Nothing to remove for statoin ID %s", stationID);
    }

}
