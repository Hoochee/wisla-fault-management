package ru.wisla.fm.health.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SankeyTest {

    private static final UUID CI_ID = UUID.fromString("c9737f8b-9dec-47c8-b068-bab36a121ea5");

    @Test
    void forDisplayDropsUuidCiAndOrientsProductToComponent() {
        Sankey legacy = new Sankey(
                List.of(
                        new SankeyNode("ci:" + CI_ID, CI_ID.toString(), "ci"),
                        new SankeyNode("component:COMMON", "COMMON", "component"),
                        new SankeyNode("product:1", "E2E Product leftover", "product")
                ),
                List.of(
                        new SankeyLink("ci:" + CI_ID, "component:COMMON", 12),
                        new SankeyLink("component:COMMON", "product:1", 12)
                )
        );

        Sankey display = Sankey.forDisplay(legacy, "E2E Product", Map.of());

        assertThat(display.nodes())
                .extracting(SankeyNode::label)
                .containsExactlyInAnyOrder("E2E Product", "COMMON");
        assertThat(display.nodes()).noneMatch(n -> "ci".equals(n.kind()));
        assertThat(display.links()).containsExactly(new SankeyLink("product:1", "component:COMMON", 12));
    }

    @Test
    void forDisplayKeepsCiWhenFqdnIsHumanReadable() {
        Sankey source = new Sankey(
                List.of(
                        new SankeyNode("product:1", "Gift Shop", "product"),
                        new SankeyNode("component:CPU", "CPU", "component"),
                        new SankeyNode("ci:" + CI_ID, CI_ID.toString(), "ci")
                ),
                List.of(
                        new SankeyLink("product:1", "component:CPU", 10),
                        new SankeyLink("component:CPU", "ci:" + CI_ID, 10)
                )
        );

        Sankey display = Sankey.forDisplay(source, "Gift Shop", Map.of(CI_ID, "checkout.gift.local"));

        assertThat(display.nodes())
                .filteredOn(n -> "ci".equals(n.kind()))
                .extracting(SankeyNode::label)
                .containsExactly("checkout.gift.local");
        assertThat(display.links()).anySatisfy(l -> {
            assertThat(l.from()).isEqualTo("component:CPU");
            assertThat(l.to()).isEqualTo("ci:" + CI_ID);
        });
    }
}
