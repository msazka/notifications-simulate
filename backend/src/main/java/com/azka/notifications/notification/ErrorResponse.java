package com.azka.notifications.notification;

import java.util.Map;

public record ErrorResponse(String message, Map<String, String> errors) {

	static ErrorResponse of(String message) {
		return new ErrorResponse(message, Map.of());
	}

}
