package ru.wisla.fm.notifications.api;

import java.util.List;

public record PushNotificationListDto(List<PushNotificationDto> items) {
}
