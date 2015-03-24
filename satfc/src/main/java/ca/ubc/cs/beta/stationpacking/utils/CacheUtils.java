package ca.ubc.cs.beta.stationpacking.utils;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Created by newmanne on 19/03/15.
 */
public class CacheUtils {

    private static RestTemplate restTemplate;

    public static BitSet toBitSet(StationPackingInstance aInstance) {
        final BitSet bitSet = new BitSet();
        aInstance.getStations().forEach(station -> bitSet.set(station.getID()));
        return bitSet;
    }

    public static BitSet toBitSet(Map<Integer, Set<Station>> answer) {
        final BitSet bitSet = new BitSet();
        answer.values().stream().forEach(stations -> stations.forEach(station -> bitSet.set(station.getID())));
        return bitSet;
    }

    public static Map<Station, Integer> stationToChannelFromChannelToStation(Map<Integer, Set<Station>> channelToStation) {
        final Map<Station, Integer> stationToChannel = new HashMap<>();
        channelToStation.entrySet().forEach(entry -> {
            entry.getValue().forEach(station -> {
                stationToChannel.put(station, entry.getKey());
            });
        });
        return stationToChannel;
    }

    public static RestTemplate getRestTemplate() {
        if (restTemplate == null) {
            restTemplate = new RestTemplate();
            final MappingJackson2HttpMessageConverter mappingJacksonHttpMessageConverter = new MappingJackson2HttpMessageConverter();
            final ObjectMapper mapper = JSONUtils.getMapper();
            // swap out the default message converter
            for (int i = 0; i < restTemplate.getMessageConverters().size(); i++) {
                if (restTemplate.getMessageConverters().get(i) instanceof MappingJackson2HttpMessageConverter) {
                    restTemplate.getMessageConverters().remove(i);
                    restTemplate.getMessageConverters().add(i, mappingJacksonHttpMessageConverter);
                }
                break;
            }
        }
        return restTemplate;
    }

}
