package com.azka.notifications.notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(UUID id, String producerId, String recipientEmail, String useCase, String locale,
		NotificationStatus status, Instant createdAt) {

	static NotificationResponse from(Notification notification) {
		return new NotificationResponse(notification.getId(), notification.getProducerId(),
				notification.getRecipientEmail(), notification.getUseCase(), notification.getLocale(),
				notification.getStatus(), notification.getCreatedAt());
	}

}
