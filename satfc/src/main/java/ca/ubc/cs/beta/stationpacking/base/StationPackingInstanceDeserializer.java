package ca.ubc.cs.beta.stationpacking.base;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import lombok.Data;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.google.common.collect.ImmutableMap;

public class StationPackingInstanceDeserializer extends
		JsonDeserializer<StationPackingInstance> {

	@Override
	public StationPackingInstance deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
		Dummy readValueAs = p.readValueAs(Dummy.class);
        if (readValueAs.getMetadata() != null) {
            return new StationPackingInstance(readValueAs.getDomains(), readValueAs.getPreviousAssignment(), readValueAs.getMetadata());
        } else {
            return new StationPackingInstance(readValueAs.getDomains(), readValueAs.getPreviousAssignment());
        }
	}
	
	@Data
	public static class Dummy {
		private ImmutableMap<Station, Set<Integer>> domains;
		private ImmutableMap<Station, Integer> previousAssignment;
		private Map<String, Object> metadata;
	}

}
