package ca.ubc.cs.beta.stationpacking.datamanagers.constraints;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.DomainStationManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.data.DataManager;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.ASolverBundle;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import org.junit.Test;

import java.io.File;
import java.util.HashSet;

import static org.junit.Assert.*;

public class ConstraintManagerTest {

    final static private String DOMAIN_PATH = Resources.getResource("data/testInterference/" + DataManager.DOMAIN_FILE).getPath();

    @Test
    public void testCompactFormat() throws Exception {
        final String interferencePath = Resources.getResource("data/testInterference/channelspecific").getPath();
        final IStationManager dm = new DomainStationManager(DOMAIN_PATH);
        final IConstraintManager constraintManager = new ChannelSpecificConstraintManager(dm, interferencePath + File.separator + DataManager.INTERFERENCES_FILE);
        doTest(dm, constraintManager);
    }

    public void doTest(IStationManager dm, IConstraintManager cm) {
        // simple CO
        assertEquals(Sets.newHashSet(s(dm, 2), s(dm, 3)), cm.getCOInterferingStations(s(dm, 1), 1));

        // adj+1 and implied CO
        assertEquals(Sets.newHashSet(s(dm, 2)), cm.getCOInterferingStations(s(dm, 1), 7));
        assertEquals(Sets.newHashSet(s(dm, 2)), cm.getCOInterferingStations(s(dm, 1), 8));
        assertEquals(Sets.newHashSet(s(dm, 2)), cm.getADJplusOneInterferingStations(s(dm, 1), 7));

        // adj+2 and implied adj+2 and implied CO
        assertEquals(Sets.newHashSet(s(dm, 6)), cm.getCOInterferingStations(s(dm, 5), 99));
        assertEquals(Sets.newHashSet(s(dm, 6)), cm.getCOInterferingStations(s(dm, 5), 100));
        assertEquals(Sets.newHashSet(s(dm, 6)), cm.getCOInterferingStations(s(dm, 5), 101));
        assertEquals(Sets.newHashSet(s(dm, 6)), cm.getADJplusOneInterferingStations(s(dm, 5), 99));
        assertEquals(Sets.newHashSet(s(dm, 6)), cm.getADJplusOneInterferingStations(s(dm, 5), 100));
        assertEquals(Sets.newHashSet(s(dm, 6)), cm.getADJplusTwoInterferingStations(s(dm, 5), 99));

        // test some negative examples
        assertEquals(new HashSet<Station>(), cm.getCOInterferingStations(s(dm, 2), 1));
        assertEquals(new HashSet<Station>(), cm.getCOInterferingStations(s(dm, 2), 777));
        assertEquals(new HashSet<Station>(), cm.getADJplusTwoInterferingStations(s(dm, 5), 100));
    }

    @Test
    public void testUnabridgedFormat() throws Exception {
        final String interferencePath = Resources.getResource("data/testInterference/unabridged").getPath();
        final IStationManager dm = new DomainStationManager(DOMAIN_PATH);
        final IConstraintManager constraintManager = new UnabridgedFormatConstraintManager(dm, interferencePath + File.separator + DataManager.INTERFERENCES_FILE);
        doTest(dm, constraintManager);
        // Test ADJ-1 and ADJ-2 constraints
        assertEquals(Sets.newHashSet(s(dm, 9)), constraintManager.getADJplusOneInterferingStations(s(dm, 10), 9));
        assertEquals(Sets.newHashSet(s(dm, 10)), constraintManager.getCOInterferingStations(s(dm, 9), 9));
        assertEquals(Sets.newHashSet(s(dm, 10)), constraintManager.getCOInterferingStations(s(dm, 9), 10));
        // Test ADJ-2 constraints
        assertEquals(Sets.newHashSet(s(dm, 9)), constraintManager.getADJplusTwoInterferingStations(s(dm, 10), 98));
        assertEquals(Sets.newHashSet(s(dm, 10)), constraintManager.getCOInterferingStations(s(dm, 9), 98));
        assertEquals(Sets.newHashSet(s(dm, 10)), constraintManager.getCOInterferingStations(s(dm, 9), 99));
        assertEquals(Sets.newHashSet(s(dm, 10)), constraintManager.getCOInterferingStations(s(dm, 9), 100));
        assertEquals(Sets.newHashSet(s(dm, 9)), constraintManager.getADJplusOneInterferingStations(s(dm, 10), 99));
        assertEquals(Sets.newHashSet(s(dm, 9)), constraintManager.getADJplusOneInterferingStations(s(dm, 10), 98));
    }

    private static Station s(IStationManager stationManager, int id) {
        return stationManager.getStationfromID(id);
    }

}