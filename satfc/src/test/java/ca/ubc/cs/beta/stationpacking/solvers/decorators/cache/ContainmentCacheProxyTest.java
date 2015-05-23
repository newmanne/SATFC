package ca.ubc.cs.beta.stationpacking.solvers.decorators.cache;

import ca.ubc.cs.beta.stationpacking.utils.CacheUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.Test;
import org.springframework.web.client.RestTemplate;

@Slf4j
public class ContainmentCacheProxyTest {

    private final String url = "http://localhost:8080/hello/neil";

    @Test
    public void testProveSATBySuperset() throws Exception {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            final HttpGet httpGet = new HttpGet(url);

            // Spawn a thread to cancel request while it's ongoing
            new Thread(() -> {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                httpGet.abort();
            }).start();
            httpClient.execute(httpGet);
        }
    }

}
