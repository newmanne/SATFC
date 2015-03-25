package ca.ubc.cs.beta.stationpacking.base;

import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import junit.framework.TestCase;
import org.junit.Test;

public class StationPackingInstanceTest extends TestCase {

    @Test
    public void testSerialization() {
        final StationPackingInstance instance = new StationPackingInstance(ImmutableMap.of(new Station(3), ImmutableSet.of(3, 4, 5)), ImmutableMap.of(new Station(3), 3), ImmutableMap.of(StationPackingInstance.NAME_KEY, "SAMPLE"));
        assertEquals("{\"domains\":{\"3\":[3,4,5]},\"previousAssignment\":{\"3\":3},\"metadata\":{\"NAME\":\"SAMPLE\"}}", JSONUtils.toString(instance));
    }

    @Test
    public void testDeserialization() {
        final StationPackingInstance instance = JSONUtils.toObject("{\"domains\":{\"3\":[3,4,5]},\"previousAssignment\":{\"3\":3}}", StationPackingInstance.class);
        System.out.println(instance.toString());
    }

}