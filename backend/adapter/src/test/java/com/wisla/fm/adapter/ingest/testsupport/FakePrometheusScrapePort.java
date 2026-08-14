package com.wisla.fm.adapter.ingest.testsupport;

import com.wisla.fm.adapter.ingest.application.port.out.PrometheusScrapePort;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FakePrometheusScrapePort implements PrometheusScrapePort {

    private final Map<String, ScrapeResult> byUrl = new LinkedHashMap<>();
    private final List<String> scrapedUrls = new ArrayList<>();

    public void returning(String url, ScrapeResult result) {
        byUrl.put(url, result);
    }

    public void returning(String url, Sample... samples) {
        byUrl.put(url, ScrapeResult.ok(List.of(samples)));
    }

    public List<String> scrapedUrls() {
        return List.copyOf(scrapedUrls);
    }

    @Override
    public ScrapeResult scrape(String url) {
        scrapedUrls.add(url);
        return byUrl.getOrDefault(url, ScrapeResult.unreachable());
    }
}
