package com.azka.notifications.notification;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

	static final int MAX_PAGE_SIZE = 100;

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

	public PageResponse<NotificationResponse> list(NotificationStatus status, int page, int size) {
		if (page < 0) {
			throw new InvalidPageRequestException("page must be 0 or greater");
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new InvalidPageRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
		}
		// Newest first; id breaks ties so rows with equal timestamps keep a stable order across pages.
		var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
		var notifications = status == null ? repository.findAll(pageable)
				: repository.findByStatus(status, pageable);
		return PageResponse.from(notifications, NotificationResponse::from);
	}

	@Transactional
	public NotificationResponse applyEvent(UUID id, String event) {
		NotificationStatus target = targetStatus(event);
		var notification = repository.findById(id).orElseThrow(() -> new NotificationNotFoundException(id));

		// One conditional UPDATE: only a PENDING notification changes.
		// A parallel or late event on a final status updates 0 rows.
		repository.updateStatusIfCurrent(id, NotificationStatus.PENDING, target);
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
