package ca.ubc.cs.beta.stationpacking.base;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.io.IOException;

/**
 * Created by newmanne on 06/03/15.
 */
public class StationDeserializer extends JsonDeserializer<Station> {

    @Override
    public Station deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException {
        return new Station(p.readValueAs(Integer.class));
    }

    public static class StationJacksonModule extends SimpleModule {
        public StationJacksonModule() {
            addKeyDeserializer(Station.class, new StationClassKeyDeserializer());
        }
    }

    public static class StationClassKeyDeserializer extends KeyDeserializer {
        @Override
        public Object deserializeKey(final String key,
                                     final DeserializationContext ctxt) throws IOException {
            return new Station(Integer.parseInt(key));
        }
    }


}
