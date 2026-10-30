package com.azka.notifications.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

	private static final String KEY = "key-1";

	private static final CreateNotificationRequest REQUEST = new CreateNotificationRequest("billing",
			"max@example.com", "dunning", "de");

	@Mock
	private NotificationRepository repository;

	@InjectMocks
	private NotificationService service;

	@Test
	void sameKeyAndSameBodyReturnsSameId() {
		var existing = stored(REQUEST, NotificationStatus.PENDING);
		when(repository.findByProducerIdAndIdempotencyKey("billing", KEY)).thenReturn(Optional.of(existing));

		var response = service.create(KEY, REQUEST);

		assertThat(response.id()).isEqualTo(existing.getId());
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void sameKeyAndDifferentBodyIsConflict() {
		var existing = stored(REQUEST, NotificationStatus.PENDING);
		when(repository.findByProducerIdAndIdempotencyKey("billing", KEY)).thenReturn(Optional.of(existing));
		var different = new CreateNotificationRequest("billing", "other@example.com", "dunning", "de");

		assertThrows(IdempotencyConflictException.class, () -> service.create(KEY, different));
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void newRequestCreatesPendingNotification() {
		when(repository.findByProducerIdAndIdempotencyKey("billing", KEY)).thenReturn(Optional.empty());
		when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

		var response = service.create(KEY, REQUEST);

		var captor = ArgumentCaptor.forClass(Notification.class);
		verify(repository).saveAndFlush(captor.capture());
		var saved = captor.getValue();
		assertThat(response.status()).isEqualTo(NotificationStatus.PENDING);
		assertThat(saved.getProducerId()).isEqualTo("billing");
		assertThat(saved.getIdempotencyKey()).isEqualTo(KEY);
		assertThat(saved.getRecipientEmail()).isEqualTo("max@example.com");
		assertThat(saved.getRequestHash()).isEqualTo(NotificationService.hash(REQUEST));
	}

	@Test
	void concurrentInsertReturnsWinnersId() {
		var winner = stored(REQUEST, NotificationStatus.PENDING);
		when(repository.findByProducerIdAndIdempotencyKey("billing", KEY)).thenReturn(Optional.empty(),
				Optional.of(winner));
		when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

		var response = service.create(KEY, REQUEST);

		assertThat(response.id()).isEqualTo(winner.getId());
	}

	@Test
	void blankKeyIsRejected() {
		assertThrows(MissingIdempotencyKeyException.class, () -> service.create(" ", REQUEST));
	}

	@Test
	void sentOnPendingDelivers() {
		var notification = stored(REQUEST, NotificationStatus.PENDING);
		when(repository.findById(notification.getId())).thenReturn(Optional.of(notification));
		when(repository.save(notification)).thenReturn(notification);

		var response = service.applyEvent(notification.getId(), "sent");

		assertThat(response.status()).isEqualTo(NotificationStatus.DELIVERED);
	}

	@Test
	void bounceOnPendingBounces() {
		var notification = stored(REQUEST, NotificationStatus.PENDING);
		when(repository.findById(notification.getId())).thenReturn(Optional.of(notification));
		when(repository.save(notification)).thenReturn(notification);

		var response = service.applyEvent(notification.getId(), "bounce");

		assertThat(response.status()).isEqualTo(NotificationStatus.BOUNCED);
	}

	@ParameterizedTest
	@EnumSource(value = NotificationStatus.class, names = { "DELIVERED", "BOUNCED" })
	void eventOnFinalStatusChangesNothing(NotificationStatus finalStatus) {
		var notification = stored(REQUEST, finalStatus);
		when(repository.findById(notification.getId())).thenReturn(Optional.of(notification));

		var afterSent = service.applyEvent(notification.getId(), "sent");
		var afterBounce = service.applyEvent(notification.getId(), "bounce");

		assertThat(afterSent.status()).isEqualTo(finalStatus);
		assertThat(afterBounce.status()).isEqualTo(finalStatus);
		verify(repository, never()).save(any());
	}

	@Test
	void eventOnUnknownIdIsNotFound() {
		var id = UUID.randomUUID();
		when(repository.findById(id)).thenReturn(Optional.empty());

		assertThrows(NotificationNotFoundException.class, () -> service.applyEvent(id, "sent"));
	}

	@Test
	void unknownEventIsRejected() {
		assertThrows(UnknownEventException.class, () -> service.applyEvent(UUID.randomUUID(), "opened"));
	}

	private static Notification stored(CreateNotificationRequest request, NotificationStatus status) {
		var notification = new Notification(request.producerId(), KEY, NotificationService.hash(request),
				request.recipientEmail(), request.useCase(), request.locale());
		ReflectionTestUtils.setField(notification, "id", UUID.randomUUID());
		notification.setStatus(status);
		return notification;
	}

}
