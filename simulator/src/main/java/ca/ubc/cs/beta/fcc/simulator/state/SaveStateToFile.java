package ca.ubc.cs.beta.fcc.simulator.state;

import ca.ubc.cs.beta.fcc.simulator.participation.Participation;
import ca.ubc.cs.beta.fcc.simulator.participation.ParticipationRecord;
import ca.ubc.cs.beta.fcc.simulator.prices.IPrices;
import ca.ubc.cs.beta.fcc.simulator.prices.PricesImpl;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.station.StationDB;
import ca.ubc.cs.beta.fcc.simulator.time.TimeTracker;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Created by newmanne on 2016-05-25.
 */
@Slf4j
public class SaveStateToFile implements IStateSaver {

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class StateFile {
        int round;
        Map<Integer, Integer> assignment;
        Map<Integer, StationState> state;
        Map<SATResult, Integer> feasibilityDistribution;
        double cputime;
        double walltime;
    }

    @AllArgsConstructor
    @Data
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StationState {
        double price;
        Participation participation;
    }

    String folder;

    public SaveStateToFile(String folder) {
        Preconditions.checkNotNull(folder);
        this.folder = folder;
    }

    @Override
    public void saveState(StationDB stationDB, IPrices prices, ParticipationRecord participation, Map<Integer, Integer> assignment, int round, Map<SATResult, Integer> feasibilityResultDistribution, TimeTracker timeTracker) {
        final String fileName = folder + File.separator + "state_" + round + ".json";
        final Map<Integer, StationState> state = new HashMap<>();
        for (IStationInfo s : stationDB.getStations()) {
            final StationState stationState = StationState.builder()
                    .price(prices.getPrice(s, Band.UHF))
                    .participation(participation.getParticipation(s))
                    .build();
            state.put(s.getId(), stationState);
        }
        final StateFile stateFile = StateFile.builder()
                .assignment(assignment)
                .state(state)
                .round(round)
                .feasibilityDistribution(feasibilityResultDistribution)
                .cputime(timeTracker.getCputime().get())
                .walltime(timeTracker.getWalltime().get())
                .build();
        final String json = JSONUtils.toString(stateFile);
        try {
            FileUtils.writeStringToFile(new File(fileName), json);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public AuctionState restoreState(StationDB stationDB) {
        try {
            final Optional<Path> mostRecentState = Files.walk(Paths.get(folder))
                    .filter(Files::isRegularFile)
                    .max(Comparator.comparingInt(a -> {
                                final String baseName = FilenameUtils.getBaseName(a.toString());
                                return Integer.parseInt(CharMatcher.DIGIT.retainFrom(baseName));
                            }
                    ));
            Preconditions.checkState(mostRecentState.isPresent(), "No state present to restore from!");
            final File stateFile = mostRecentState.get().toFile();
            log.info("Restoring state from {}", stateFile.getAbsolutePath());
            final IPrices prices = new PricesImpl();
            final ParticipationRecord participationRecord = new ParticipationRecord();
            final String json = FileUtils.readFileToString(stateFile);
            final StateFile stateFile1 = JSONUtils.toObject(json, StateFile.class);
            for (Map.Entry<Integer, StationState> entry : stateFile1.getState().entrySet()) {
                final int id = entry.getKey();
                final StationState record = entry.getValue();
                final IStationInfo station = stationDB.getStationById(id);
                prices.setPrice(station, Band.UHF, record.getPrice());
                participationRecord.setParticipation(station, record.getParticipation());
            }
            return AuctionState.builder()
                    .benchmarkPrices(prices)
                    .participation(participationRecord)
                    .round(stateFile1.getRound())
                    .assignment(stateFile1.getAssignment())
                    .build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
