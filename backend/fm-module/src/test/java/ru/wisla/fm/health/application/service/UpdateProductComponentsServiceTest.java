package ru.wisla.fm.health.application.service;

import org.junit.jupiter.api.Test;
import ru.wisla.fm.health.domain.CiMembership;
import ru.wisla.fm.health.domain.ComponentDraft;
import ru.wisla.fm.health.domain.ComponentNode;
import ru.wisla.fm.health.domain.InfluenceType;
import ru.wisla.fm.health.domain.ProductTopology;
import ru.wisla.fm.health.domain.SavedComponent;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpdateProductComponentsServiceTest {

    private static final UUID PRODUCT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CI_POWER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CI_CPU = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final InMemoryHealthPorts.Topology topology = new InMemoryHealthPorts.Topology();

    @Test
    void omitComponentsLeavesSlotsUnchanged() {
        topology.with(productWithCommon());
        int before = topology.replaceCalls;

        List<SavedComponent> saved = service().update(PRODUCT_ID, null);

        assertThat(topology.replaceCalls).isEqualTo(before);
        assertThat(saved).extracting(SavedComponent::code).containsExactly("COMMON");
    }

    @Test
    void zeroTotalWeightIsRejected() {
        topology.with(productWithCommon());

        assertThatThrownBy(() -> service().update(PRODUCT_ID, List.of(
                draft("POWER", 0, "critical", List.of(CI_POWER)),
                draft("CPU", 0, "weighted", List.of(CI_CPU))
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("weight");
        assertThat(topology.replaceCalls).isZero();
    }

    @Test
    void ciCannotJoinTwoComponentsOfOneProduct() {
        topology.with(productWithCommon());

        assertThatThrownBy(() -> service().update(PRODUCT_ID, List.of(
                draft("POWER", 50, "critical", List.of(CI_POWER)),
                draft("CPU", 50, "weighted", List.of(CI_POWER))
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CI");
        assertThat(topology.replaceCalls).isZero();
    }

    @Test
    void bindNewCisToCommonCreatesSlotWhenMissing() {
        topology.with(new ProductTopology(
                PRODUCT_ID, "demo", "moscow", "dc1", List.of(), List.of(CI_CPU), List.of()
        ));

        List<SavedComponent> saved = service().bindNewCisToCommon(PRODUCT_ID, List.of(CI_CPU));

        assertThat(topology.bindCalls).isEqualTo(1);
        assertThat(saved).extracting(SavedComponent::code).contains("COMMON");
        assertThat(saved.stream().filter(c -> "COMMON".equals(c.code())).findFirst().orElseThrow()
                .ciIds()).contains(CI_CPU);
    }

    @Test
    void alreadySlottedCiIsNotMovedToCommon() {
        topology.with(new ProductTopology(
                PRODUCT_ID,
                "demo",
                "moscow",
                "dc1",
                List.of(),
                List.of(CI_POWER, CI_CPU),
                List.of(
                        new ComponentNode(
                                UUID.randomUUID(), "POWER", "POWER", 20, InfluenceType.CRITICAL, 100, 0,
                                List.of(new CiMembership(CI_POWER, null))
                        ),
                        new ComponentNode(
                                UUID.randomUUID(), "COMMON", "COMMON", 80, InfluenceType.WEIGHTED, 100, 1,
                                List.of()
                        )
                )
        ));

        List<SavedComponent> saved = service().bindNewCisToCommon(PRODUCT_ID, List.of(CI_POWER, CI_CPU));

        SavedComponent power = saved.stream().filter(c -> "POWER".equals(c.code())).findFirst().orElseThrow();
        SavedComponent common = saved.stream().filter(c -> "COMMON".equals(c.code())).findFirst().orElseThrow();
        assertThat(power.ciIds()).contains(CI_POWER);
        assertThat(common.ciIds()).contains(CI_CPU).doesNotContain(CI_POWER);
    }

    private UpdateProductComponentsService service() {
        return new UpdateProductComponentsService(topology);
    }

    private static ProductTopology productWithCommon() {
        return new ProductTopology(
                PRODUCT_ID,
                "demo",
                "moscow",
                "dc1",
                List.of(),
                List.of(),
                List.of(new ComponentNode(
                        UUID.nameUUIDFromBytes("COMMON".getBytes()),
                        "COMMON",
                        "COMMON",
                        100,
                        InfluenceType.WEIGHTED,
                        100,
                        0,
                        List.of()
                ))
        );
    }

    private static ComponentDraft draft(String code, int weight, String influence, List<UUID> ciIds) {
        return new ComponentDraft(code, code, weight, influence, 100, ciIds, 0);
    }
}
