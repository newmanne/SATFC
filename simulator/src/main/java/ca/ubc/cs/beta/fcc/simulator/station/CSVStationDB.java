package ca.ubc.cs.beta.fcc.simulator.station;

import ca.ubc.cs.beta.fcc.simulator.parameters.SimulatorParameters;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.fcc.simulator.utils.BandHelper;
import ca.ubc.cs.beta.fcc.simulator.utils.SimulatorUtils;
import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import com.google.common.collect.ImmutableMap;
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
public class CSVStationDB implements StationDB {

    private final Map<Integer, IStationInfo> data;

    public CSVStationDB(String infoFile, SimulatorParameters.IVolumeCalculator volumeCalculator,
                        SimulatorParameters.IValueCalculator valueCalculator, IStationManager stationManager,
                        int highest, boolean uhfOnly, List<SimulatorParameters.IPredicateFactory> ignores,
                        Function<IStationInfo, IStationInfo> decorate) {
        final Map<Integer, IStationInfo> dataTmp = new HashMap<>();
        log.info("Reading station info from {}", infoFile);
        final Iterable<CSVRecord> records = SimulatorUtils.readCSV(infoFile);
        for (CSVRecord record : records) {
            IStationInfo stationInfo;
            final int id = Integer.parseInt(record.get("FacID"));
            final Nationality nationality = Nationality.valueOf(record.get("Country"));
            // Account for Canadian stations having a highest of 1 lower than the clearing target
            int stationHighest = nationality.equals(Nationality.US) ? highest : highest - 1;
            final ImmutableSet<Integer> domain = ImmutableSet.copyOf(stationManager.getRestrictedDomain(new Station(id), stationHighest, uhfOnly));
            if (domain.size() == 0) {
                log.info("Station {} has no domain, skipping", id);
                continue;
            }
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
            stationInfo = decorate.apply(stationInfo);
            dataTmp.put(id, stationInfo);
        }
        final Predicate<IStationInfo> ignore = ignores.stream()
                .map(p -> p.create(dataTmp))
                .reduce(Predicate::or).get();
        data = ImmutableMap.copyOf(Maps.filterValues(dataTmp, x -> !ignore.test(x)));
        // ugly...
        final Set<StationInfo> americanStations = data.values().stream().filter(s -> s.getNationality().equals(Nationality.US)).map(s -> (StationInfo) s).collect(Collectors.toSet());
        volumeCalculator.setVolumes(americanStations);
        valueCalculator.setValues(americanStations);
        log.info("Finished reading stations");
        final Map<Band, List<IStationInfo>> collect = getStations().stream().collect(Collectors.groupingBy(IStationInfo::getHomeBand));
        for (Band band : collect.keySet()) {
            log.info("{} {} stations", collect.get(band).size(), band);
        }
    }

    public CSVStationDB(String infoFile, SimulatorParameters.IVolumeCalculator volumeCalculator, SimulatorParameters.IValueCalculator valueCalculator, IStationManager stationManager, int highest, boolean uhfOnly) {
        this(infoFile, volumeCalculator, valueCalculator, stationManager, highest, uhfOnly, Lists.newArrayList(x -> y -> false), x -> x);
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
