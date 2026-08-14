package ru.wisla.fm.health.domain;

public enum InfluenceType {
    WEIGHTED,
    CRITICAL;

    public static InfluenceType fromWire(String value) {
        if (value == null || value.isBlank()) {
            return WEIGHTED;
        }
        return switch (value.toLowerCase()) {
            case "critical" -> CRITICAL;
            case "weighted" -> WEIGHTED;
            default -> throw new IllegalArgumentException("Unknown influence type: " + value);
        };
    }

    public String toWire() {
        return name().toLowerCase();
    }
}
