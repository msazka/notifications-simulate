package com.azka.notifications.notification;

public class UnknownEventException extends RuntimeException {

	public UnknownEventException(String event) {
		super("Unknown event '" + event + "', expected 'sent' or 'bounce'");
	}

}
