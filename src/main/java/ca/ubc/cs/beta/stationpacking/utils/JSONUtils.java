package ca.ubc.cs.beta.stationpacking.utils;

import java.io.IOException;

import lombok.Getter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Created by newmanne on 02/12/14.
 */
public class JSONUtils {

    @Getter
    private static final ObjectMapper mapper = new ObjectMapper();

    public static <T> T toObject(String jsonString, Class<T> klazz) {
        try {
            return mapper.readValue(jsonString, klazz);
        } catch (IOException e) {
            throw new RuntimeException("Couldn't deserialize string " + jsonString + " into type " + klazz, e);
        }
    }

    public static String toString(Object object) {
    	return toString(object, false);
    }
    
    public static String toString(Object object, boolean pretty) {
        try {
        	final String json;
        	if (pretty) {
        		json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        	} else {
        		json = mapper.writeValueAsString(object);
        	}
            return json;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Couldn't serialize object " + object, e);
        }
    }

}