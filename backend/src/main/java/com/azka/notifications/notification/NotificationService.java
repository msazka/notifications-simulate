package com.azka.notifications.notification;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

	private final NotificationRepository repository;

	public NotificationService(NotificationRepository repository) {
		this.repository = repository;
	}

	// Deliberately not @Transactional: after a unique-constraint violation the
	// transaction would be rollback-only, and we still need to re-read the winner.
	public CreateNotificationResponse create(String idempotencyKey, CreateNotificationRequest request) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new MissingIdempotencyKeyException();
		}
		String hash = hash(request);

		var existing = repository.findByProducerIdAndIdempotencyKey(request.producerId(), idempotencyKey);
		if (existing.isPresent()) {
			return replay(existing.get(), idempotencyKey, hash);
		}

		var notification = new Notification(request.producerId(), idempotencyKey, hash, request.recipientEmail(),
				request.useCase(), request.locale());
		try {
			return toCreateResponse(repository.saveAndFlush(notification));
		}
		catch (DataIntegrityViolationException ex) {
			// A concurrent request with the same producer and key inserted first.
			var winner = repository.findByProducerIdAndIdempotencyKey(request.producerId(), idempotencyKey)
				.orElseThrow(() -> ex);
			return replay(winner, idempotencyKey, hash);
		}
	}

	public List<NotificationResponse> list(NotificationStatus status) {
		var notifications = status == null ? repository.findAllByOrderByCreatedAtDesc()
				: repository.findByStatusOrderByCreatedAtDesc(status);
		return notifications.stream().map(NotificationResponse::from).toList();
	}

	public NotificationResponse applyEvent(UUID id, String event) {
		NotificationStatus target = targetStatus(event);
		var notification = repository.findById(id).orElseThrow(() -> new NotificationNotFoundException(id));

		// DELIVERED and BOUNCED are final; events on them are ignored.
		if (notification.getStatus() == NotificationStatus.PENDING) {
			notification.setStatus(target);
			notification = repository.save(notification);
		}
		return NotificationResponse.from(notification);
	}

	private static NotificationStatus targetStatus(String event) {
		if (event == null) {
			throw new UnknownEventException(null);
		}
		return switch (event) {
			case "sent" -> NotificationStatus.DELIVERED;
			case "bounce" -> NotificationStatus.BOUNCED;
			default -> throw new UnknownEventException(event);
		};
	}

	private static CreateNotificationResponse replay(Notification existing, String idempotencyKey, String hash) {
		if (!existing.getRequestHash().equals(hash)) {
			throw new IdempotencyConflictException(idempotencyKey);
		}
		return toCreateResponse(existing);
	}

	private static CreateNotificationResponse toCreateResponse(Notification notification) {
		return new CreateNotificationResponse(notification.getId(), notification.getStatus());
	}

	// producerId is part of the idempotency key itself, so only the remaining fields are hashed.
	static String hash(CreateNotificationRequest request) {
		String canonical = String.join("\u0000", request.recipientEmail(), request.useCase(), request.locale());
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 not available", ex);
		}
	}

}
