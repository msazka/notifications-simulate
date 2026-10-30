package com.azka.notifications.notification;

import java.util.UUID;

public record CreateNotificationResponse(UUID id, NotificationStatus status) {
}
