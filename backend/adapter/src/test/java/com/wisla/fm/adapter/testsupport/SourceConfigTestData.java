package com.wisla.fm.adapter.testsupport;

import com.wisla.fm.adapter.persistence.entity.SourceConfigSnapshot;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class SourceConfigTestData {

    private SourceConfigTestData() {
    }

    public static SourceConfigSnapshot snapshot(
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

    public static SourceConfigSnapshot snapshot(
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
            var constructor = SourceConfigSnapshot.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            SourceConfigSnapshot entity = constructor.newInstance();
            set(entity, "sourceId", sourceId);
            set(entity, "sourceKey", sourceKey);
            set(entity, "apiKeyHash", apiKeyHash);
            set(entity, "endpoint", endpoint);
            set(entity, "filterRules", filterRules != null ? filterRules : Map.of());
            set(entity, "blocked", blocked);
            set(entity, "ttlExpiresAt", ttlExpiresAt);
            set(entity, "createdAt", createdAt);
            set(entity, "updatedAt", updatedAt);
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
