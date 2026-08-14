package ru.wisla.fm.health.api;

import java.util.List;

public record SankeyDto(List<SankeyNodeDto> nodes, List<SankeyLinkDto> links) {
}
