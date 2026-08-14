package ru.wisla.fm.health.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring-free tests of the Monq formula {@code H = min(h_direct, h_ratio)} and damage rules.
 */
class HealthCalculatorTest {

    private static final UUID PRODUCT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CI_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CI_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CI_C = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final HealthCalculator calculator = new HealthCalculator();

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "fatal,    0",
            "critical, 25",
            "major,    50",
            "minor,    62",
            "warning,  75",
            "normal,   100",
            ",         100"
    })
    void signalMapsToCiHealth(String severity, int expected) {
        assertThat(HealthCalculator.ciHealthFromWorstSeverity(severity)).isEqualTo(expected);
    }

    @Test
    void weightedAverageWhenNoCriticalBreach() {
        ProductTopology topology = productWithOneComponent(
                InfluenceType.WEIGHTED,
                List.of(membership(CI_A, 50), membership(CI_B, 50))
        );

        HealthCalculation result = calculator.calculate(topology, Map.of(CI_A, 100, CI_B, 50));

        assertThat(result.healthPercent()).isEqualTo(75);
        assertThat(result.damagePercent()).isEqualTo(25);
    }

    @Test
    void criticalLinkCapsHealth() {
        ProductTopology topology = new ProductTopology(
                PRODUCT_ID,
                "demo",
                "moscow",
                "dc1",
                List.of(),
                List.of(CI_A, CI_B, CI_C),
                List.of(
                        component("POWER", InfluenceType.CRITICAL, 100, List.of(membership(CI_A, 20))),
                        component("CPU", InfluenceType.WEIGHTED, 40, List.of(membership(CI_B, 40))),
                        component("HDD", InfluenceType.WEIGHTED, 40, List.of(membership(CI_C, 40)))
                )
        );

        HealthCalculation result = calculator.calculate(
                topology,
                Map.of(CI_A, 25, CI_B, 100, CI_C, 100)
        );

        assertThat(result.healthPercent()).isEqualTo(25);
        assertThat(result.damagePercent()).isEqualTo(75);
    }

    @Test
    void zeroTotalWeightYields100WhenNoCriticalLinks() {
        ProductTopology topology = productWithOneComponent(
                InfluenceType.WEIGHTED,
                List.of(membership(CI_A, 0), membership(CI_B, 0))
        );

        HealthCalculation result = calculator.calculate(topology, Map.of(CI_A, 50, CI_B, 25));

        assertThat(result.healthPercent()).isEqualTo(100);
        assertThat(result.damagePercent()).isEqualTo(0);
    }

    @Test
    void weightedDamageIsShareTimesDeficit() {
        ProductTopology topology = productWithOneComponent(
                InfluenceType.WEIGHTED,
                List.of(membership(CI_A, 40), membership(CI_B, 60))
        );

        HealthCalculation result = calculator.calculate(topology, Map.of(CI_A, 50, CI_B, 100));

        SankeyLink link = result.payload().sankey().links().stream()
                .filter(l -> l.to().equals("ci:" + CI_A))
                .findFirst()
                .orElseThrow();
        assertThat(link.damage()).isEqualTo(20);
    }

    @Test
    void criticalDamageIgnoresWeightShare() {
        ProductTopology topology = productWithOneComponent(
                InfluenceType.CRITICAL,
                List.of(membership(CI_A, 10))
        );

        HealthCalculation result = calculator.calculate(topology, Map.of(CI_A, 25));

        SankeyLink link = result.payload().sankey().links().stream()
                .filter(l -> l.to().equals("ci:" + CI_A))
                .findFirst()
                .orElseThrow();
        assertThat(link.damage()).isEqualTo(75);
    }

    @Test
    void equalMinimaSplitDamageEvenly() {
        ProductTopology topology = productWithOneComponent(
                InfluenceType.CRITICAL,
                List.of(membership(CI_A, 50), membership(CI_B, 50))
        );

        HealthCalculation result = calculator.calculate(topology, Map.of(CI_A, 25, CI_B, 25));

        List<Integer> damages = result.payload().sankey().links().stream()
                .filter(l -> l.to().startsWith("ci:"))
                .map(SankeyLink::damage)
                .toList();
        assertThat(damages).containsExactlyInAnyOrder(37, 38);
        assertThat(damages.stream().mapToInt(Integer::intValue).sum()).isEqualTo(75);
    }

    @Test
    void missingCiHealthIsTreatedAs100() {
        ProductTopology topology = productWithOneComponent(
                InfluenceType.WEIGHTED,
                List.of(membership(CI_A, 100))
        );

        HealthCalculation result = calculator.calculate(topology, Map.of());

        assertThat(result.healthPercent()).isEqualTo(100);
    }

    @Test
    void sankeyFlowsFromProductToComponentsWithReadableLabels() {
        ProductTopology topology = new ProductTopology(
                PRODUCT_ID,
                "Gift Shop",
                "moscow",
                "dc1",
                List.of(),
                List.of(CI_A, CI_B),
                List.of(
                        component("POWER", InfluenceType.WEIGHTED, 50, List.of(membership(CI_A, 50))),
                        component("COMMON", InfluenceType.WEIGHTED, 50, List.of(membership(CI_B, 50)))
                )
        );

        HealthCalculation result = calculator.calculate(topology, Map.of(CI_A, 50, CI_B, 100));
        Sankey sankey = result.payload().sankey();

        assertThat(sankey.nodes())
                .anySatisfy(n -> {
                    assertThat(n.kind()).isEqualTo("product");
                    assertThat(n.label()).isEqualTo("Gift Shop");
                })
                .anySatisfy(n -> {
                    assertThat(n.id()).isEqualTo("component:POWER");
                    assertThat(n.label()).isEqualTo("POWER");
                });
        assertThat(sankey.links())
                .anySatisfy(l -> {
                    assertThat(l.from()).startsWith("product:");
                    assertThat(l.to()).isEqualTo("component:POWER");
                    assertThat(l.damage()).isGreaterThan(0);
                });
        assertThat(sankey.links()).noneMatch(l -> l.to().startsWith("product:"));
        assertThat(sankey.links()).noneMatch(l -> l.from().startsWith("ci:"));
    }

    private static ProductTopology productWithOneComponent(InfluenceType influence, List<CiMembership> cis) {
        return new ProductTopology(
                PRODUCT_ID,
                "demo",
                "moscow",
                "dc1",
                List.of(),
                cis.stream().map(CiMembership::ciId).toList(),
                List.of(component("COMMON", influence, 100, cis))
        );
    }

    private static ComponentNode component(
            String code,
            InfluenceType influence,
            int weight,
            List<CiMembership> cis
    ) {
        return new ComponentNode(
                UUID.nameUUIDFromBytes(code.getBytes()),
                code,
                code,
                weight,
                influence,
                100,
                0,
                cis
        );
    }

    private static CiMembership membership(UUID ciId, int weight) {
        return new CiMembership(ciId, weight);
    }
}
