package com.azka.notifications.notification;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "notification",
		uniqueConstraints = @UniqueConstraint(name = "uk_notification_producer_key",
				columnNames = { "producer_id", "idempotency_key" }),
		indexes = { @Index(name = "idx_notification_created_at", columnList = "created_at, id"),
				@Index(name = "idx_notification_status_created_at", columnList = "status, created_at, id") })
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "producer_id", nullable = false)
	private String producerId;

	@Column(name = "idempotency_key", nullable = false)
	private String idempotencyKey;

	@Column(name = "request_hash", nullable = false, length = 64)
	private String requestHash;

	@Column(nullable = false)
	private String recipientEmail;

	@Column(nullable = false)
	private String useCase;

	@Column(nullable = false)
	private String locale;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private NotificationStatus status;

	@Column(nullable = false)
	private Instant createdAt;

	protected Notification() {
	}

	public Notification(String producerId, String idempotencyKey, String requestHash, String recipientEmail,
			String useCase, String locale) {
		this.producerId = producerId;
		this.idempotencyKey = idempotencyKey;
		this.requestHash = requestHash;
		this.recipientEmail = recipientEmail;
		this.useCase = useCase;
		this.locale = locale;
		this.status = NotificationStatus.PENDING;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getProducerId() {
		return producerId;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public String getRequestHash() {
		return requestHash;
	}

	public String getRecipientEmail() {
		return recipientEmail;
	}

	public String getUseCase() {
		return useCase;
	}

	public String getLocale() {
		return locale;
	}

	public NotificationStatus getStatus() {
		return status;
	}

	public void setStatus(NotificationStatus status) {
		this.status = status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
