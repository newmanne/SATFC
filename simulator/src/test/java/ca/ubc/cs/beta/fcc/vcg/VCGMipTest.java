package ca.ubc.cs.beta.fcc.vcg;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.GZIPOutputStream;

import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.stationpacking.execution.Converter;
import ca.ubc.cs.beta.stationpacking.execution.problemgenerators.problemparsers.StupidProblemParser;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.Cleanup;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.alg.NeighborIndex;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.base.Splitter;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;

import ca.ubc.cs.beta.fcc.simulator.parameters.UnitVolumeCalculator;
import ca.ubc.cs.beta.fcc.simulator.station.CSVStationDB;
import ca.ubc.cs.beta.fcc.simulator.station.Nationality;
import ca.ubc.cs.beta.fcc.simulator.utils.SimulatorUtils;
import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.Constraint;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.data.DataManager;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.data.ManagerBundle;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.ConstraintGrouper;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;
import lombok.extern.slf4j.Slf4j;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

/**
 * Created by newmanne on 2016-05-26.
 */
@Slf4j
public class VCGMipTest {

    static IConstraintManager constraintManager;
    static IStationManager stationManager;

    static String INFO_FILE = "/ubc/cs/research/arrow/satfc/simulator/data/station_info_v2.csv";
    static String VOLUMES_FILE = "/ubc/cs/research/arrow/satfc/simulator/data/volumes.csv";


    @Test
    public void gzip() throws Exception {
        final String filename = "/tmp/hello.gz";
        final String contents = "hello chicken";
        GZIPOutputStream zip = new GZIPOutputStream(new FileOutputStream(filename));
        @Cleanup
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(zip, "UTF-8"));
        writer.append(contents);
    }
    

//    @Test
//    public void databaseConnectionTest() throws NamingException, SQLException, IOException {
//        final String host = "arrowdb.cs.ubc.ca";
//        final int port = 4545;
//        final String databaseName = "AuctionSimulation";
//        final String url = "jdbc:mysql://" + host + ":" + port + "/" + databaseName + "?allowMultiQueries=true";
//
////        Context context = new InitialContext();
////        DataSource dataSource = (DataSource) context.lookup(url);
//        @Cleanup
//        final Connection connection = DriverManager.getConnection(url, "newmanne", "interim pw");
////        @Cleanup
////        final Connection connection = dataSource.getConnection("newmanne", "interim pw");
//        final Statement statement = connection.createStatement();
//        final ResultSet rs = statement.executeQuery(
//                "SELECT `Generator_0_Problems`.`stations`, `Generator_0_Previous_Assignments`.`assignment`, `Generator_0_Problems`.`name`\n" +
//                        "FROM `Generator_0_Problems` INNER JOIN `Generator_0_Previous_Assignments`\n" +
//                        "ON `Generator_0_Problems`.`previous_assignment_id` = `Generator_0_Previous_Assignments`.`id` \n" +
//                        "WHERE `Generator_0_Problems`.`cached` = 0\n" +
//                        "AND `Generator_0_Problems`.`greedy` = 0\n" +
//                        "AND `Generator_0_Problems`.`band` = 'UHF';"
//        );
//        ResultSetMetaData rsmd = rs.getMetaData();
//        int columnsNumber = rsmd.getColumnCount();
//        while (rs.next()) {
//            final List<Integer> stations = JSONUtils.getMapper().readValue(rs.getString(1), new TypeReference<List<Integer>>() {});
//            final Map<Integer, Integer> assignment = JSONUtils.getMapper().readValue(rs.getString(2), new TypeReference<Map<Integer, Integer>>() {});
//            final StupidProblemParser.SimulatorRedisProblem problem = StupidProblemParser.SimulatorRedisProblem.builder()
//                    .interference("nov2015")
//                    .maxChannel(36)
//                    .previousAssignment(assignment)
//                    .stations(stations)
//                    .name("Blah")
//                    .build();
//            log.info(rs.getString(3));
//        }
//
//
//    }

//    @BeforeClass
//    public static void init() {
//        ManagerBundle bundle = bundle("/Users/newmanne/research/interference-data/nov2015");
//        constraintManager = bundle.getConstraintManager();
//        stationManager = bundle.getStationManager();
//    }

//    @Test
//    public void neighbourHoods() {
//        log.info("There are {} stations in the manager", stationManager.getStations().size());
//        int highest = 29;
//        IStationDB stationDB = new CSVStationDB(INFO_FILE, new UnitVolumeCalculator(), new UnitValueCalculator(), stationManager, highest, false);
//        final Map<Station, Set<Integer>> domains = stationManager.getStations()
//                .stream()
//                .filter(s -> stationDB.getStationById(s.getID()) != null)
//                .filter(s -> stationDB.getStationById(s.getID()).getNationality().equals(Nationality.US))
//                .collect(Collectors.toMap(s -> s, s -> stationManager.getRestrictedDomain(s, highest, true)));
//        final SimpleGraph<Station, DefaultEdge> constraintGraph = ConstraintGrouper.getConstraintGraph(domains, constraintManager);
//        // NY
//        final List<Integer> input = Arrays.asList(1328,73881,6048,47535,73356,9610,22206,73207,14322);
//        // LA
//        // final List<Integer> input = Arrays.asList(282,21422,22208,33742,191101,3167,60026,13058,35670,35123,47906,38430,26231, 9628, 167309);
//        final Set<Station> startingPoint = new HashSet<>(input).stream()
//                .filter(s -> !stationManager.getRestrictedDomain(new Station(s), 29, true).isEmpty())
//                .filter(s -> stationDB.getStationById(s) != null)
//                .map(Station::new)
//                .collect(Collectors.toSet());
//        System.out.println(Joiner.on(',').join(startingPoint));
//        final Iterable<Set<Station>> stationsToPack = new AddNeighbourLayerStrategy().getStationsToPack(constraintGraph, startingPoint);
//        for (Set<Station> stations : stationsToPack) {
//            System.out.println(Joiner.on(',').join(stations));
//        }
//
//
//        final Set<Station> stationActual = Iterables.get(stationsToPack, 1);
//        log.info("Checking graph for {} stations", stationActual.size());
//        final SimpleGraph<Station, DefaultEdge> graph = ConstraintGrouper.getConstraintGraph(domainsFromStations(stationActual), constraintManager);
//        final double avgDegree = graph.vertexSet().stream().mapToInt(graph::degreeOf).average().getAsDouble();
//        log.info("Average degree in graph is {}, {}", avgDegree, graph.isAllowingMultipleEdges());
//
//
////        final List<Integer> integers = Arrays.asList(14322, 73207, 9610, 73356, 22206, 47535, 6048, 48481, 14050, 10213, 73318, 23142, 74215, 191340, 25453, 60654, 51568, 30577, 73206, 167543, 73333, 72313, 53115, 36989, 73344, 3072, 168834, 55305, 60553, 60555, 9739, 51980, 70158, 50063, 60560, 51984, 147, 73879, 67866, 67993, 13594, 13595, 56092, 73374, 72096, 73375, 13602, 10153, 23338, 74156, 73264, 43952, 73263, 63153, 59442, 64690, 74419, 74422, 55350, 33081, 47929, 74170, 28480, 38336, 7623, 39884, 48465, 20818, 25682, 12499, 72278, 74197, 48477, 70493, 74464, 53734, 14312, 57832, 14315, 57837, 11260, 10758, 67077, 167948, 7692, 10259, 34329, 168478, 9762, 64547, 70184, 68135, 71218, 78908, 22591, 16455, 4688, 25683, 2650, 50780, 7780, 13929, 617, 13933, 84088, 27772, 57476, 65670, 60551, 60552, 46728, 72335, 10897, 65684, 51864, 69271, 46755, 70309, 40618, 50347, 40619, 66219, 74416, 40626, 61111, 62136, 62137, 22207, 77515, 69328, 15569, 71905, 39656, 73964, 60653, 39664, 71928, 7933, 73982, 9987, 21252, 9989, 9990, 62219, 9999, 2325, 48406, 48408, 47904, 10019, 47401, 68396, 23341, 74034, 69940, 40758, 69944, 40759, 69943, 6463, 20287, 64833, 66378, 53065, 6476, 25932, 71508, 14682, 18783, 64352, 58725, 52075, 16747, 363, 65387, 74091, 52077, 51567, 30576, 25456, 73083, 3978, 10132, 65942, 65944, 65943, 73113, 69532, 412, 413, 73120, 415, 43424, 54176, 72098, 72099, 74151, 57274, 450, 25045, 71127, 41436);
////        final Map<Station, Set<Integer>> d = stationManager.getStations().stream()
////                .filter(s -> integers.contains(s.getID()))
////                .collect(Collectors.toMap(s -> s, s -> stationManager.getRestrictedDomain(s, 29, true)));
////        int nConstraints = Iterables.size(constraintManager.getAllRelevantConstraints(d));
////        log.info("There are {} constraints", nConstraints);
//    }
//
//    Map<Station, Set<Integer>> domainsFromStations(Set<Station> stations) {
//        return stations.stream()
//                .collect(Collectors.toMap(s -> s, s -> stationManager.getRestrictedDomain(s, 29, true)));
//    }

//    @Test
//    public void constraints() throws IOException {
//        final Set<Station> NYC3 = Splitter.on(',').splitToList("14322,73207,9610,73356,22206,47535,6048,48481,14050,10213,73318,74214,23142,74215,191340,25453,60654,51568,30577,73206,167543,73333,72313,53115,36989,73344,3072,168834,55305,60553,60555,9739,51980,70158,50063,60560,51984,147,73879,67866,67993,13594,13595,56092,73374,72096,73375,13602,10153,23338,68137,74156,73264,43952,73263,63153,59442,64690,74419,74422,55350,33081,47929,74170,28480,38336,7623,39884,48465,20818,25682,12499,72278,74197,48477,30429,70493,74464,53734,74216,14312,57832,57837,11260,168449,10758,67077,167948,7692,10259,73238,34329,168478,9762,64547,9766,68136,70184,68135,71218,57907,57908,78908,22591,16455,4688,25683,2650,50780,7780,13929,617,13933,629,84088,27772,57476,65670,60551,60552,46728,72335,10897,65684,51864,69271,46755,70309,40618,50347,40619,66219,74416,40626,61111,62136,62137,22207,77515,69328,15567,15569,71905,39656,73964,60653,39664,71928,7933,73982,9987,21252,9989,9990,62219,9999,2325,48406,48408,48413,47904,191262,10019,47401,68396,23341,74034,69940,68403,40758,69944,40759,69943,6463,20287,64833,66378,53065,6476,25932,191822,71508,14682,18783,64352,58725,52075,16747,363,65387,74091,52077,51567,30576,25456,73083,3978,10132,65942,65944,65943,73113,69532,412,413,73120,415,43424,54176,72098,72099,74151,57274,450,72145,25045,71127,41436").stream().map(i -> new Station(Integer.parseInt(i))).collect(Collectors.toSet());
//        final Map<Station, Set<Integer>> domains = stationManager.getStations().stream()
//                .filter(s -> NYC3.contains(s))
//                .collect(Collectors.toMap(s -> s, s -> stationManager.getRestrictedDomain(s, 29, true)));
//        Map<Pair<Station, Station>, Integer> weights = new HashMap<>();
//        Map<Station, Integer> stationWeight = new HashMap<>();
//        for (Constraint constraint : constraintManager.getAllRelevantConstraints(domains)) {
//            Station lower = constraint.getSource().getID() < constraint.getTarget().getID() ? constraint.getSource() : constraint.getTarget();
//            Station upper = constraint.getSource().equals(lower) ? constraint.getTarget() : constraint.getSource();
//            stationWeight.merge(lower, 1, (existing,def) -> existing + 1);
//            stationWeight.merge(upper, 1, (existing,def) -> existing + 1);
//            weights.merge(Pair.of(lower, upper), 1, (existing,def) -> existing + 1);
//        }
//        FileUtils.writeStringToFile(new File("/tmp/output.json"), JSONUtils.toString(weights));
//        FileUtils.writeStringToFile(new File("/tmp/weights.json"), JSONUtils.toString(stationWeight));
//    }

    public static ManagerBundle bundle(String path) {
        try {
            final DataManager m = new DataManager();
            m.addData(path);
            return m.getData(path);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

//    @Test
//    public void compare() throws Exception {
//
//        final Set<Integer> ids = Splitter.on(',').splitToList("14322,73207,9610,73356,22206,47535,6048,48481,14050,10213,73318,74214,23142,74215,191340,25453,60654,51568,30577,73206,167543,73333,72313,53115,36989,73344,3072,168834,55305,60553,60555,9739,51980,70158,50063,60560,51984,147,73879,67866,67993,13594,13595,56092,73374,72096,73375,13602,10153,23338,68137,74156,73264,43952,73263,63153,59442,64690,74419,74422,55350,33081,47929,74170,28480,38336,7623,39884,48465,20818,25682,12499,72278,74197,48477,30429,70493,74464,53734,74216,14312,57832,57837,11260,168449,10758,67077,167948,7692,10259,73238,34329,168478,9762,64547,9766,68136,70184,68135,71218,57907,57908,78908,22591,16455,4688,25683,2650,50780,7780,13929,617,13933,629,84088,27772,57476,65670,60551,60552,46728,72335,10897,65684,51864,69271,46755,70309,40618,50347,40619,66219,74416,40626,61111,62136,62137,22207,77515,69328,15567,15569,71905,39656,73964,60653,39664,71928,7933,73982,9987,21252,9989,9990,62219,9999,2325,48406,48408,48413,47904,191262,10019,47401,68396,23341,74034,69940,68403,40758,69944,40759,69943,6463,20287,64833,66378,53065,6476,25932,191822,71508,14682,18783,64352,58725,52075,16747,363,65387,74091,52077,51567,30576,25456,73083,3978,10132,65942,65944,65943,73113,69532,412,413,73120,415,43424,54176,72098,72099,74151,57274,450,72145,25045,71127,41436").stream().map(s -> Integer.parseInt(s)).collect(Collectors.toSet());
//        final Map<Station, Set<Integer>> domainsNov = ids.stream().map(s -> stationManager.getStationfromID(s)).collect(Collectors.toMap(s -> s, s -> stationManager.getRestrictedDomain(s, 29, true)));
//        final Set<Constraint> novConstraints = Sets.newHashSet(constraintManager.getAllRelevantConstraints(domainsNov));
//
//        final ManagerBundle fcc = bundle("/Users/newmanne/research/interference-data/032416SC46U");
//        final Map<Station, Set<Integer>> domainsFCC = ids.stream().map(s -> fcc.getStationManager().getStationfromID(s)).collect(Collectors.toMap(s -> s, s -> fcc.getStationManager().getRestrictedDomain(s, 29, true)));
//        final Set<Constraint> fccConstraints = Sets.newHashSet(fcc.getConstraintManager().getAllRelevantConstraints(domainsFCC));
//
//        log.info("{} {} {}", novConstraints.equals(fccConstraints), novConstraints.size(), fccConstraints.size());
//
//
//
//    }

//    public static Map<Station, Integer> icCounts(Map<Station, Set<Integer>> domains, CSVStationDB stationDB) {
//        final SimpleGraph<Station, DefaultEdge> constraintGraph = ConstraintGrouper.getConstraintGraph(domains, constraintManager);
//        final NeighborIndex<Station, DefaultEdge> neighborIndex = new NeighborIndex<>(constraintGraph);
//        final Map<Station, Integer> icMap = new HashMap<>();
//        log.info("{} stations (roughly)", domains.keySet().size());
//        int j = 0;
//
//        Set<Station> stuff = new HashSet<>();
//        stuff.addAll(domains.keySet());
////        stuff.add(new Station(53586));
//
//        for (Station a : stuff) {
//            j++;
//            if (j % 100 == 0) {
//                log.info(""+j);
//            }
//            if (stationDB.getStationById(a.getID()).getNationality().equals(Nationality.CA)) {
//                continue;
//            }
//            int icNum = 0;
//            int caNum = 0;
//
//            Set<Station> neighbours = neighborIndex.neighborsOf(a);
////            if (neighbours.stream().anyMatch(n -> stationDB.getStationById(n.getID()).getNationality().equals(Nationality.CA))) {
////                log.info("Skipping {} because has CDN neighbours", a);
////            	continue;
////            }
//
//
//            for (Station b : neighbours) {
//                int overallChanMax = 0;
//                int CAD = 0;
//                boolean canNeighbour = stationDB.getStationById(b.getID()).getNationality().equals(Nationality.CA);
//                for (int chanA : domains.get(a)) {
//                    int chanMax = 0;
//                    for (int chanB : domains.get(b)) {
//                        if (!constraintManager.isSatisfyingAssignment(a, chanA, b, chanB)) {
//                            chanMax += 1;
//                        }
//                    }
//                    overallChanMax = Math.max(overallChanMax, chanMax);
//                    if (overallChanMax >= 3) {
//                        break;
//                    }
//                }
//                icNum += overallChanMax;
//
//                if (stationDB.getStationById(b.getID()).getNationality().equals(Nationality.CA)) {
//                    caNum += overallChanMax;
//                }
//            }
//
//            icNum = (int) Math.round(icNum - caNum + caNum * 2.3);
//
//            icMap.put(a, icNum);
////            if (caNum > 0) {
////                log.info("CA num for {}, {}", a, caNum);
////            }
//        }
//        return icMap;
//    }
//
//    @Test
//    public void t() {
//        final Map<Station, Set<Integer>> domains = stationManager.getDomains();
//        final CSVStationDB stationDB = new CSVStationDB(INFO_FILE, new UnitVolumeCalculator(), stationManager, 52, false);
//        for (final Station s : domains.keySet()) {
//            final Set<Integer> domain = domains.get(s);
//            final Set<Integer> ufh  = domain.stream().filter(c -> c >= 14).collect(Collectors.toSet());
//            if (!ufh.isEmpty()) {
//                int minChan = ufh.stream().mapToInt(c -> c).min().getAsInt();
//                if (minChan > 25) {
//                    System.out.println(s.getID() + ":" + minChan);
//                }
//            }
//        }

//        final Iterable<CSVRecord> csvRecords = SimulatorUtils.readCSV("/Users/newmanne/research/ic_counts.csv");
//        final Map<Station, Integer> fccCounts = new HashMap<>();
//        for (CSVRecord record : csvRecords) {
//            fccCounts.put(new Station(Integer.parseInt(record.get("FacID"))), Integer.parseInt(record.get("Interference")));
//        }
//        final Map<Station, Integer> counts = icCounts(stationManager.getDomains(), stationDB);
//        log.info("FCC has {} records, we have {}", fccCounts.size(), counts.size());
//
//        final MapDifference<Station, Integer> difference = Maps.difference(counts, fccCounts);
//        log.info("Size difference: {}", difference.entriesDiffering().size());
//        System.out.println("DIFFERENCE:" + difference.entriesDiffering());
//    }

//    public class FCCVolumeCalculator {
//
//        public void duh() {
//            final Map<StationInfo, Double> nonNormlalizedVolumes = new HashMap<>();
//            final IStationManager stationManager = getStationManager();
//            final IConstraintManager constraintManager = getConstraintManager();
//            log.debug("There are {} stations in the manager", stationManager.getStations().size());
//            final Map<Station, Set<Integer>> domains = stationManager.getStations()
//                    .stream()
//                    .filter(s -> stationDB.getStationById(s.getID()) != null)
//                    .collect(Collectors.toMap(s -> s, s -> stationDB.getStationById(s.getID()).getDomain()));
//            final SimpleGraph<Station, DefaultEdge> constraintGraph = ConstraintGrouper.getConstraintGraph(domains, getConstraintManager());
//            final NeighborIndex<Station, DefaultEdge> neighborIndex = new NeighborIndex<>(constraintGraph);
//            final Map<Station, Integer> icMap = new HashMap<>();
//            for (Station a : domains.keySet()) {
//                int icNum = 0;
//                for (Station b : neighborIndex.neighborsOf(a)) {
//                    int overallChanMax = 0;
//                    for (Integer chanA : domains.get(a)) {
//                        int chanMax = 0;
//                        for (Integer chanB : domains.get(b)) {
//                            if (!constraintManager.isSatisfyingAssignment(a, chanA, b, chanB)) {
//                                chanMax += 1;
//                            }
//                        }
//                        overallChanMax = Math.max(overallChanMax, chanMax);
//                    }
//                    icNum += overallChanMax;
//                }
//                icMap.put(a, icNum);
//            }
//
//            for (StationInfo s: stationInfo) {
//                int pop = s.getPopulation();
//
//            }
//        }
//
//    }
//
//
//    @Test
//    public void intcounts() throws IOException {
//        final Set<Station> NYC3 = Splitter.on(',').splitToList("14322,73207,9610,73356,22206,47535,6048,48481,14050,10213,73318,74214,23142,74215,191340,25453,60654,51568,30577,73206,167543,73333,72313,53115,36989,73344,3072,168834,55305,60553,60555,9739,51980,70158,50063,60560,51984,147,73879,67866,67993,13594,13595,56092,73374,72096,73375,13602,10153,23338,68137,74156,73264,43952,73263,63153,59442,64690,74419,74422,55350,33081,47929,74170,28480,38336,7623,39884,48465,20818,25682,12499,72278,74197,48477,30429,70493,74464,53734,74216,14312,57832,57837,11260,168449,10758,67077,167948,7692,10259,73238,34329,168478,9762,64547,9766,68136,70184,68135,71218,57907,57908,78908,22591,16455,4688,25683,2650,50780,7780,13929,617,13933,629,84088,27772,57476,65670,60551,60552,46728,72335,10897,65684,51864,69271,46755,70309,40618,50347,40619,66219,74416,40626,61111,62136,62137,22207,77515,69328,15567,15569,71905,39656,73964,60653,39664,71928,7933,73982,9987,21252,9989,9990,62219,9999,2325,48406,48408,48413,47904,191262,10019,47401,68396,23341,74034,69940,68403,40758,69944,40759,69943,6463,20287,64833,66378,53065,6476,25932,191822,71508,14682,18783,64352,58725,52075,16747,363,65387,74091,52077,51567,30576,25456,73083,3978,10132,65942,65944,65943,73113,69532,412,413,73120,415,43424,54176,72098,72099,74151,57274,450,72145,25045,71127,41436").stream().map(i -> new Station(Integer.parseInt(i))).collect(Collectors.toSet());
//        Map<Station, Set<Integer>> domains = stationManager.getStations().stream()
//                .collect(Collectors.toMap(s -> s, s -> stationManager.getDomain(s)));
//        Map<Station, Integer> counts = icCounts(domains);
//        FileUtils.writeStringToFile(new File("/tmp/icmap_fcc.json"), JSONUtils.toString(counts));
//
//        domains = stationManager.getStations().stream()
//                .collect(Collectors.toMap(s -> s, s -> stationManager.getRestrictedDomain(s, 29, false)));
//        counts = icCounts(domains);
//        FileUtils.writeStringToFile(new File("/tmp/icmap_29.json"), JSONUtils.toString(counts));
//
//        domains = stationManager.getStations().stream()
//                .filter(s -> NYC3.contains(s))
//                .collect(Collectors.toMap(s -> s, s -> stationManager.getRestrictedDomain(s, 29, true)));
//        counts = icCounts(domains);
//        FileUtils.writeStringToFile(new File("/tmp/icmap_simulator.json"), JSONUtils.toString(counts));
//
//        Map<Pair<Station, Station>, Integer> weights = new HashMap<>();
//        Map<Station, Integer> stationWeight = new HashMap<>();
//        for (Constraint constraint : constraintManager.getAllRelevantConstraints(domains)) {
//            Station lower = constraint.getSource().getID() < constraint.getTarget().getID() ? constraint.getSource() : constraint.getTarget();
//            Station upper = constraint.getSource().equals(lower) ? constraint.getTarget() : constraint.getSource();
//            stationWeight.merge(lower, 1, (existing,def) -> existing + 1);
//            stationWeight.merge(upper, 1, (existing,def) -> existing + 1);
//            weights.merge(Pair.of(lower, upper), 1, (existing,def) -> existing + 1);
//        }
//        FileUtils.writeStringToFile(new File("/tmp/output.json"), JSONUtils.toString(weights));
//        FileUtils.writeStringToFile(new File("/tmp/weights.json"), JSONUtils.toString(stationWeight));
//    }

//    @Test
//    public void test() {
//        ManagerBundle fccConstraints = bundle("/Users/newmanne/research/interference-data/032416SC46U");
//        ManagerBundle publicConstraints = bundle("/Users/newmanne/research/interference-data/nov2015");
//
//        log.info("FCC Domain hash {}", fccConstraints.getStationManager().getDomainHash());
//        log.info("Public Domain hash {}", publicConstraints.getStationManager().getDomainHash());
//
//
//
//        log.info("FCC constraint hash {}", fccConstraints.getConstraintManager().getConstraintHash());
//        log.info("Public Domain hash {}", publicConstraints.getConstraintManager().getConstraintHash());
//
//        AMapBasedConstraintManager fccC = (AMapBasedConstraintManager) fccConstraints.getConstraintManager();
//        AMapBasedConstraintManager publicC = (AMapBasedConstraintManager) publicConstraints.getConstraintManager();
//
//        final Map<Station, Map<Integer, Set<Station>>> fccCo = fccC.getFCOConstraints();
//        final Map<Station, Map<Integer, Set<Station>>> publicCo = publicC.getFCOConstraints();
//        final Set<Station> stations = Sets.union(fccCo.keySet(), publicCo.keySet());
//        for (Station s : stations) {
//            final Map<Integer, Set<Station>> fccMap = fccCo.getOrDefault(s, new HashMap<>());
//            final Map<Integer, Set<Station>> publicMap = publicCo.getOrDefault(s, new HashMap<>());
//            final Map<Integer, ValueDifference<Set<Station>>> differences = Maps.difference(fccMap, publicMap).entriesDiffering();
//            for (Map.Entry<Integer, ValueDifference<Set<Station>>> diff : differences.entrySet()) {
//                final Sets.SetView<Station> diffStations = Sets.difference(Sets.union(diff.getValue().leftValue(), diff.getValue().rightValue()), Sets.intersection(diff.getValue().leftValue(), diff.getValue().rightValue()));
//                log.info("Station {} on channel {} with {}", s, diff.getKey(), diffStations);
//            }
//        }
//
//
//    }

}