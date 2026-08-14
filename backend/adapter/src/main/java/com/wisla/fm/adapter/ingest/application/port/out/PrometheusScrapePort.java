package com.wisla.fm.adapter.ingest.application.port.out;

import java.util.List;

public interface PrometheusScrapePort {

    ScrapeResult scrape(String url);

    record Sample(String name, double value) {
    }

    record ScrapeResult(boolean reachable, List<Sample> samples) {

        public static ScrapeResult ok(List<Sample> samples) {
            return new ScrapeResult(true, List.copyOf(samples));
        }

        public static ScrapeResult unreachable() {
            return new ScrapeResult(false, List.of());
        }
    }
}
