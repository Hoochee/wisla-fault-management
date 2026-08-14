package com.wisla.fm.adapter.ingest.adapter.out.http;

import com.wisla.fm.adapter.ingest.application.port.out.PrometheusScrapePort;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Prometheus text exposition into unlabeled (or first-sample) metric values.
 */
final class PrometheusTextParser {

    private static final Pattern SAMPLE = Pattern.compile(
            "^([a-zA-Z_:][a-zA-Z0-9_:]*)(?:\\{[^}]*})?\\s+([+-]?(?:NaN|Inf|Infinity|[0-9]*\\.?[0-9]+(?:[eE][+-]?[0-9]+)?))(?:\\s+\\d+)?\\s*$"
    );

    private PrometheusTextParser() {
    }

    static List<PrometheusScrapePort.Sample> parse(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        List<PrometheusScrapePort.Sample> samples = new ArrayList<>();
        for (String rawLine : body.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            Matcher matcher = SAMPLE.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            samples.add(new PrometheusScrapePort.Sample(matcher.group(1), Double.parseDouble(normalize(matcher.group(2)))));
        }
        return List.copyOf(samples);
    }

    private static String normalize(String value) {
        if ("+Inf".equals(value) || "+Infinity".equals(value) || "Inf".equals(value) || "Infinity".equals(value)) {
            return "Infinity";
        }
        if ("-Inf".equals(value) || "-Infinity".equals(value)) {
            return "-Infinity";
        }
        return value;
    }
}
