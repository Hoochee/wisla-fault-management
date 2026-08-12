package com.wisla.fm.adapter.ingest.adapter.out.http;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class FmModuleClient {

    private final AtomicReference<String> lastReachability = new AtomicReference<>("unknown");

    public IngestResult forwardIngest(String baseUrl, String ingestApiKey, Map<String, Object> ingestBody) {
        String uri = UriComponentsBuilder.fromPath("/api/v1/ingest")
                .queryParam("sourceKey", ingestApiKey)
                .build()
                .toUriString();
        long start = System.currentTimeMillis();
        try {
            var response = WebClient.create(baseUrl)
                    .post()
                    .uri(uri)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .header("X-Api-Key", ingestApiKey)
                    .bodyValue(ingestBody)
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(10))
                    .block();
            lastReachability.set("reachable");
            int status = response != null ? response.getStatusCode().value() : 202;
            return new IngestResult(true, status, System.currentTimeMillis() - start, null, false);
        } catch (WebClientResponseException ex) {
            lastReachability.set("reachable");
            int status = ex.getStatusCode().value();
            boolean retryable = status >= 500 || status == 408;
            return new IngestResult(false, status, System.currentTimeMillis() - start, ex.getMessage(), retryable);
        } catch (Exception ex) {
            lastReachability.set("unreachable");
            return new IngestResult(false, null, System.currentTimeMillis() - start, ex.getMessage(), true);
        }
    }

    public String getReachabilityStatus(WebClient fmModuleWebClient, boolean healthCheckEnabled) {
        if (!healthCheckEnabled) {
            return "unknown";
        }
        try {
            fmModuleWebClient.get()
                    .uri("/health")
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(3))
                    .onErrorResume(ex -> Mono.empty())
                    .block();
            lastReachability.set("reachable");
        } catch (Exception ex) {
            lastReachability.set("unreachable");
        }
        return lastReachability.get();
    }

    public record IngestResult(
            boolean success,
            Integer httpStatus,
            long latencyMs,
            String error,
            boolean retryable
    ) {
    }
}
