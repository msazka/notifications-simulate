package com.azka.notifications.notification;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

	private final NotificationService service;

	public NotificationController(NotificationService service) {
		this.service = service;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	public CreateNotificationResponse create(@RequestHeader("Idempotency-Key") String idempotencyKey,
			@Valid @RequestBody CreateNotificationRequest request) {
		return service.create(idempotencyKey, request);
	}

	@GetMapping
	public PageResponse<NotificationResponse> list(@RequestParam(required = false) NotificationStatus status,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return service.list(status, page, size);
	}

	@PostMapping("/{id}/events")
	public NotificationResponse applyEvent(@PathVariable UUID id, @RequestBody EventRequest request) {
		return service.applyEvent(id, request.event());
	}

}
