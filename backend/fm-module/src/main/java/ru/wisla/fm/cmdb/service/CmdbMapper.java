package ru.wisla.fm.cmdb.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import ru.wisla.fm.admin.api.ConfigurationItemDto;
import ru.wisla.fm.cmdb.domain.ConfigurationItemEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class CmdbMapper {

    private final ObjectMapper objectMapper;

    public CmdbMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ConfigurationItemDto toDto(ConfigurationItemEntity ci, List<UUID> productIds) {
        return new ConfigurationItemDto(
                ci.getId(),
                ci.getFqdn(),
                ci.getCiType(),
                ci.getSystemName(),
                ci.getSubsystemName(),
                ci.getSoftware(),
                productIds,
                parseStringList(ci.getTags()),
                parseStringMap(ci.getExternalIds())
        );
    }

    private List<String> parseStringList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, String> parseStringMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
}
