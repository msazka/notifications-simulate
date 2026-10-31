package com.azka.notifications.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
		// findById returns the row as stored after the conditional UPDATE.
		var reloaded = stored(REQUEST, NotificationStatus.DELIVERED);
		var id = reloaded.getId();
		when(repository.findById(id)).thenReturn(Optional.of(reloaded));

		var response = service.applyEvent(id, "sent");

		var order = inOrder(repository);
		order.verify(repository).updateStatusIfCurrent(id, NotificationStatus.PENDING, NotificationStatus.DELIVERED);
		order.verify(repository).findById(id);
		assertThat(response.status()).isEqualTo(NotificationStatus.DELIVERED);
	}

	@Test
	void bounceOnPendingBounces() {
		// findById returns the row as stored after the conditional UPDATE.
		var reloaded = stored(REQUEST, NotificationStatus.BOUNCED);
		var id = reloaded.getId();
		when(repository.findById(id)).thenReturn(Optional.of(reloaded));

		var response = service.applyEvent(id, "bounce");

		var order = inOrder(repository);
		order.verify(repository).updateStatusIfCurrent(id, NotificationStatus.PENDING, NotificationStatus.BOUNCED);
		order.verify(repository).findById(id);
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
		//verify(repository, never()).save(any());
		verify(repository).updateStatusIfCurrent(notification.getId(), NotificationStatus.PENDING, NotificationStatus.BOUNCED);
		verify(repository).updateStatusIfCurrent(notification.getId(), NotificationStatus.PENDING, NotificationStatus.DELIVERED);
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

	@Test
	void listRequestsNewestFirstWithIdTiebreak() {
		when(repository.findAll(any(Pageable.class))).thenReturn(Page.empty());

		service.list(null, 2, 20);

		var captor = ArgumentCaptor.forClass(Pageable.class);
		verify(repository).findAll(captor.capture());
		var pageable = captor.getValue();
		assertThat(pageable.getPageNumber()).isEqualTo(2);
		assertThat(pageable.getPageSize()).isEqualTo(20);
		assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
	}

	@Test
	void listWithStatusFiltersByStatus() {
		when(repository.findByStatus(eq(NotificationStatus.PENDING), any(Pageable.class))).thenReturn(Page.empty());

		service.list(NotificationStatus.PENDING, 0, 20);

		verify(repository).findByStatus(eq(NotificationStatus.PENDING), any(Pageable.class));
		verify(repository, never()).findAll(any(Pageable.class));
	}

	@Test
	void listMapsPageToPageResponse() {
		var notification = stored(REQUEST, NotificationStatus.PENDING);
		var pageable = PageRequest.of(1, 2);
		when(repository.findAll(any(Pageable.class)))
			.thenReturn(new PageImpl<>(List.of(notification), pageable, 3));

		var response = service.list(null, 1, 2);

		assertThat(response.items()).extracting(NotificationResponse::id).containsExactly(notification.getId());
		assertThat(response.page()).isEqualTo(1);
		assertThat(response.size()).isEqualTo(2);
		assertThat(response.totalElements()).isEqualTo(3);
		assertThat(response.totalPages()).isEqualTo(2);
	}

	@Test
	void listAcceptsMaxPageSize() {
		when(repository.findAll(any(Pageable.class))).thenReturn(Page.empty());

		service.list(null, 0, NotificationService.MAX_PAGE_SIZE);

		verify(repository).findAll(any(Pageable.class));
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, -1, 101 })
	void listRejectsSizeOutOfRange(int size) {
		assertThrows(InvalidPageRequestException.class, () -> service.list(null, 0, size));
		verifyNoInteractions(repository);
	}

	@Test
	void listRejectsNegativePage() {
		assertThrows(InvalidPageRequestException.class, () -> service.list(null, -1, 20));
		verifyNoInteractions(repository);
	}

	private static Notification stored(CreateNotificationRequest request, NotificationStatus status) {
		var notification = new Notification(request.producerId(), KEY, NotificationService.hash(request),
				request.recipientEmail(), request.useCase(), request.locale());
		ReflectionTestUtils.setField(notification, "id", UUID.randomUUID());
		notification.setStatus(status);
		return notification;
	}

}
