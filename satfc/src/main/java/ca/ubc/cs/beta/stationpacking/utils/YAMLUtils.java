package ca.ubc.cs.beta.stationpacking.utils;

import lombok.Getter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.guava.GuavaModule;

/**
 * Created by newmanne on 05/10/15.
 * Used to read and write YAML
 */
public class YAMLUtils {

    @Getter
    private static final ObjectMapper mapper;

    static {
        mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new GuavaModule());
        mapper.registerModule(new SATFCJacksonModule());
    }

}
