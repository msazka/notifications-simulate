package com.azka.notifications.notification;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateNotificationRequest(
		@NotBlank(message = "producerId must not be blank")
		@Pattern(regexp = SLUG, message = "producerId " + SLUG_RULE) String producerId,
		@NotBlank(message = "recipientEmail must not be blank")
		@Email(message = "recipientEmail must be a valid email address") String recipientEmail,
		@NotBlank(message = "useCase must not be blank")
		@Pattern(regexp = SLUG, message = "useCase " + SLUG_RULE) String useCase,
		@NotNull(message = "locale is required")
		@Pattern(regexp = "de|en|fr|it|nl", message = "locale must be one of de, en, fr, it, nl") String locale) {

	// Identifiers, not free text: producerId is part of the idempotency key, so "Billing" and "billing "
	// must not count as different producers. Max 64 matches the column length.
	static final String SLUG = "[a-z0-9][a-z0-9._-]{0,63}";

	static final String SLUG_RULE = "must be 1-64 characters: lowercase letters, digits, '.', '_' or '-', "
			+ "starting with a letter or digit";

}
