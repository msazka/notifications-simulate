package com.azka.notifications.notification;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateNotificationRequest(
		@NotBlank(message = "producerId must not be blank") String producerId,
		@NotBlank(message = "recipientEmail must not be blank")
		@Email(message = "recipientEmail must be a valid email address") String recipientEmail,
		@NotBlank(message = "useCase must not be blank") String useCase,
		@NotNull(message = "locale is required")
		@Pattern(regexp = "de|en|fr|it|nl", message = "locale must be one of de, en, fr, it, nl") String locale) {
}
