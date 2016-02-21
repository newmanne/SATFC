package ca.ubc.cs.beta.stationpacking.execution;

import ca.ubc.cs.beta.stationpacking.facade.SATFCFacade;
import ca.ubc.cs.beta.stationpacking.facade.SATFCFacadeBuilder;
import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Sets;
import lombok.Cleanup;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by newmanne on 2016-02-18.
 */
@Slf4j
public class AuctionCSVParser {

    public static void main(String[] args) throws Exception {
        final String INTERFERENCE_ROOT = "/ubc/cs/research/arrow/satfc/instances/interference-data/";

        @Cleanup
        final SATFCFacade facade = new SATFCFacadeBuilder()
                .setServerURL("http://localhost:8080/satfcserver")
                .build();

        final String CSV_FILE_ROOT = "/ubc/cs/research/arrow/satfc/instances/rawdata/csvs";
        final File[] csvFolders = new File(CSV_FILE_ROOT).listFiles(File::isDirectory);
        for (File auctionDir : Arrays.stream(csvFolders).filter(d -> Integer.parseInt(d.getName()) >= 3240).collect(Collectors.toList())) {
            final String auction = auctionDir.getName();
            log.info("Reading files from auction {}", auction);
            final File[] csvFiles = auctionDir.listFiles();
            for (File csvFile : csvFiles) {
                final List<ProblemAndAnswer> problems = parseAuctionFile(auction, csvFile.getAbsolutePath());
                for (ProblemAndAnswer problem : problems) {
                    if (problem.getInterference().equals("102015SC44U") && problem.getResult().equals("yes")) {
                        int maxChan = problem.getDomains().values().stream().flatMap(Collection::stream).mapToInt(Integer::valueOf).max().getAsInt();
                        if (StationPackingUtils.UHF_CHANNELS.contains(maxChan)) {
                            facade.solve(problem.getDomains(), new HashMap<>(), 60.0, 1, INTERFERENCE_ROOT + File.separator + problem.getInterference(), problem.getInstanceName());
                        }
                    }
                }
            }
        }

    }

    public static List<ProblemAndAnswer> parseAuctionFile(String auction, String fileName) throws IOException {
        final List<String> lines = Files.readAllLines(Paths.get(fileName));
        String interferenceData = null;
        Double cutoff = null;
        Long sequenceId = null;
        int i;
        for (i = 0; i < lines.size(); i++) {
            final String line = lines.get(i);
            final List<String> split = Splitter.on(',').splitToList(line);
            // Parse header
            if (split.get(0).equals("constraints")) {
                interferenceData = split.get(1);
            } else if (split.get(0).equals("timeout")) {
                cutoff = Double.parseDouble(split.get(1)) / 1000.0;
            } else if (split.get(0).equals("sequence_id")) {
                sequenceId = Long.parseLong(split.get(1));
            } else if (split.get(0).equals("shared_info")) {
                i++;
                break;
            } else {
                throw new IllegalStateException("Unrecognized key " + line);
            }
        }
        Preconditions.checkNotNull(interferenceData);
        Preconditions.checkNotNull(cutoff);
        Preconditions.checkNotNull(sequenceId);

        final Map<Integer, Integer> prevAssign = new HashMap<>();
        final Map<Integer, Set<Integer>> currentProblem = new HashMap<>();

        // Parse assignment

        for (; i < lines.size(); i++ ) {
            final String line = lines.get(i);
            final List<String> split = Splitter.on(',').splitToList(line);
            if (split.get(0).equals("problems")) {
                i++;
                break;
            } else {
                int station = Integer.parseInt(split.get(0));
                int prevChan = Integer.parseInt(split.get(1));
                Set<Integer> domains = split.subList(2,split.size()).stream().map(Integer::parseInt).collect(Collectors.toSet());
                prevAssign.put(station, prevChan);
                currentProblem.put(station, domains);
            }
        }

        // Parse problems
        final List<ProblemAndAnswer> problems = new ArrayList<>();

        int p = 0;
        for (; i < lines.size(); i++) {
            p += 1;
            final String line = lines.get(i);
            final List<String> split = Splitter.on(',').splitToList(line);
            if (split.get(0).equals("answers")) {
                i++;
                break;
            } else {
                int station = Integer.parseInt(split.get(0));
                Set<Integer> domains = split.subList(1,split.size()).stream().map(Integer::parseInt).collect(Collectors.toSet());
                Map<Integer, Set<Integer>> problem = new HashMap<>(currentProblem);
                problem.put(station, domains);

                final String fName = new File(fileName).getName().replace(".csv", "");
                final String name = auction + "_" + fName + "_" + sequenceId + "_" + p + ".srpk";
                problems.add(new ProblemAndAnswer(problem, prevAssign, interferenceData, name));
            }
        }

        // Parse answers
        p = 0;
        for (; i < lines.size(); i++) {
            p += 1;
            final String line = lines.get(i);
            final List<String> split = Splitter.on(',').splitToList(line);
            final String result = split.get(1);
            final Set<String> results = Sets.newHashSet("yes", "no", "unknown", "error", "interrupted");
            Preconditions.checkState(results.contains(result), "Unrecognized result %s", result);
            problems.get(p - 1).setResult(result);
            if (result.equals("yes")) {
                Map<Integer, Integer> stationToChannel = new HashMap<>();
                for (int j = 2; j < split.size(); j++) {
                    final List<String> sc = Splitter.on(':').splitToList(split.get(j));
                    int station = Integer.parseInt(sc.get(0));
                    int chan = Integer.parseInt(sc.get(1));
                    stationToChannel.put(station, chan);
                }
                problems.get(p - 1).setAnswer(stationToChannel);
            }
        }

        return problems;
    }

    @Data
    public static class ProblemAndAnswer {

        private final Map<Integer, Set<Integer>> domains;
        private final Map<Integer, Integer> previousAssignment;
        private final String interference;
        private final String instanceName;
        private String result;
        private Map<Integer, Integer> answer;
        
    }

}
