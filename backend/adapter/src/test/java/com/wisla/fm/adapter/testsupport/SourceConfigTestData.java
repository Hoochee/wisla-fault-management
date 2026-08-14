package com.wisla.fm.adapter.testsupport;

import com.wisla.fm.adapter.ingest.adapter.out.persistence.SourceConfigSnapshotJpaEntity;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class SourceConfigTestData {

    private SourceConfigTestData() {
    }

    public static SourceConfigSnapshotJpaEntity snapshot(
            UUID sourceId,
            String sourceKey,
            String apiKeyHash,
            String endpoint,
            Map<String, Object> filterRules,
            boolean blocked
    ) {
        Instant now = Instant.now();
        return snapshot(
                sourceId,
                sourceKey,
                apiKeyHash,
                endpoint,
                filterRules,
                blocked,
                now.plusSeconds(3600),
                now,
                now
        );
    }

    public static SourceConfigSnapshotJpaEntity snapshot(
            UUID sourceId,
            String sourceKey,
            String apiKeyHash,
            String endpoint,
            Map<String, Object> filterRules,
            boolean blocked,
            Instant ttlExpiresAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        try {
            var constructor = SourceConfigSnapshotJpaEntity.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            SourceConfigSnapshotJpaEntity entity = constructor.newInstance();
            set(entity, "sourceId", sourceId);
            set(entity, "sourceKey", sourceKey);
            set(entity, "apiKeyHash", apiKeyHash);
            set(entity, "endpoint", endpoint);
            set(entity, "filterRules", filterRules != null ? filterRules : Map.of());
            set(entity, "blocked", blocked);
            set(entity, "ttlExpiresAt", ttlExpiresAt);
            set(entity, "createdAt", createdAt);
            set(entity, "updatedAt", updatedAt);
            set(entity, "sourceType", "push_rest");
            set(entity, "schedule", null);
            set(entity, "parserConfig", Map.of());
            return entity;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to build source config snapshot", ex);
        }
    }

    private static void set(Object target, String fieldName, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
