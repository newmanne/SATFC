package ca.ubc.cs.beta.stationpacking.test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Ignore;
import org.junit.Test;

import ca.ubc.cs.beta.stationpacking.facade.datamanager.data.DataManager;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;

/**
 * Created by newmanne on 28/01/16.
 */
public class GenConstraintHashes {

    public final String folder = "/ubc/cs/research/arrow/satfc/instances/interference-data";

    @Test
    @Ignore
    public void test() throws IOException {
        final Map<String, Map<String, String>> output = new HashMap<>();
        final DataManager dm = new DataManager();
        for (File constraintSet : new File(folder).listFiles(File::isDirectory)) {
            output.put(constraintSet.getName(), new HashMap<>());
            dm.addData(constraintSet.getAbsolutePath());
            output.get(constraintSet.getName()).put("domain", dm.getData(constraintSet.getAbsolutePath()).getCacheCoordinate().getDomainHash());
            output.get(constraintSet.getName()).put("interference", dm.getData(constraintSet.getAbsolutePath()).getCacheCoordinate().getInterferenceHash());
        }
        System.out.println(JSONUtils.toString(output));
    }

}
