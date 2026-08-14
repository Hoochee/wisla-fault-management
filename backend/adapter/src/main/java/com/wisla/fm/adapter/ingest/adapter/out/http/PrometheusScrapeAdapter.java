package com.wisla.fm.adapter.ingest.adapter.out.http;

import com.wisla.fm.adapter.ingest.application.port.out.PrometheusScrapePort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PrometheusScrapeAdapter implements PrometheusScrapePort {

    private final RestClient restClient;

    @Autowired
    public PrometheusScrapeAdapter() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(5_000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    PrometheusScrapeAdapter(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public ScrapeResult scrape(String url) {
        try {
            String body = restClient.get().uri(url).retrieve().body(String.class);
            if (body == null) {
                return ScrapeResult.unreachable();
            }
            return ScrapeResult.ok(PrometheusTextParser.parse(body));
        } catch (Exception ignored) {
            return ScrapeResult.unreachable();
        }
    }
}
