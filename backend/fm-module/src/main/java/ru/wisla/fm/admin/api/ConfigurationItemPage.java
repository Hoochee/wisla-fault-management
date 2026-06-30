package ru.wisla.fm.admin.api;

import ru.wisla.fm.common.api.PageMeta;

import java.util.List;

public record ConfigurationItemPage(
        List<ConfigurationItemDto> items,
        PageMeta page
) {
}
