package com.wisla.fm.adapter.ingest.adapter.out.http;

import com.wisla.fm.adapter.ingest.application.port.out.PrometheusScrapePort.Sample;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrometheusTextParserTest {

    @Test
    void parsesUnlabeledGaugeAndSkipsComments() {
        String body = """
                # HELP process_cpu_usage Process CPU
                # TYPE process_cpu_usage gauge
                process_cpu_usage 0.90
                up 1
                """;

        assertThat(PrometheusTextParser.parse(body))
                .containsExactly(new Sample("process_cpu_usage", 0.90), new Sample("up", 1.0));
    }

    @Test
    void parsesLabeledSampleByName() {
        String body = """
                up{job="catalog"} 0
                """;

        assertThat(PrometheusTextParser.parse(body))
                .containsExactly(new Sample("up", 0.0));
    }
}
