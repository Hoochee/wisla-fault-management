package ru.wisla.fm.admin.api;

import ru.wisla.fm.common.api.PageMeta;
import ru.wisla.fm.identity.api.UserDto;

import java.util.List;

public record UserPage(
        List<UserDto> items,
        PageMeta page
) {
}
