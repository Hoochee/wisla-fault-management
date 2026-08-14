package ru.wisla.fm.health.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import ru.wisla.fm.health.domain.SnapshotPayload;

@Component
public class SnapshotPayloadMapper {

    private final ObjectMapper objectMapper;

    public SnapshotPayloadMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(SnapshotPayload payload) {
        if (payload == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    public SnapshotPayload fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new SnapshotPayload(java.util.List.of(), java.util.List.of(),
                    new ru.wisla.fm.health.domain.Sankey(java.util.List.of(), java.util.List.of()));
        }
        try {
            return objectMapper.readValue(json, SnapshotPayload.class);
        } catch (Exception e) {
            return new SnapshotPayload(java.util.List.of(), java.util.List.of(),
                    new ru.wisla.fm.health.domain.Sankey(java.util.List.of(), java.util.List.of()));
        }
    }
}
