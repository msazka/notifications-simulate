package com.azka.notifications.notification;

public class MissingIdempotencyKeyException extends RuntimeException {

	public MissingIdempotencyKeyException() {
		super("Idempotency-Key header is required");
	}

}
