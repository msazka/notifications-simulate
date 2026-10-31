package com.azka.notifications.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * Runs the real, transactional {@link NotificationService} against the database, for rules
 * that depend on JPA behaviour a mocked repository cannot show.
 */
@DataJpaTest
@Import(NotificationService.class)
class NotificationServiceIntegrationTest {

	@Autowired
	private NotificationService service;

	@Autowired
	private NotificationRepository repository;

	@Test
	void eventResponseShowsNewStatus() {
		// saveAndFlush leaves a managed PENDING instance in the persistence context,
		// so a stale read would return PENDING.
		var id = repository.saveAndFlush(newNotification()).getId();

		var response = service.applyEvent(id, "sent");

		assertThat(response.status()).isEqualTo(NotificationStatus.DELIVERED);
	}

	private static Notification newNotification() {
		return new Notification("billing", "key-9", "hash", "max@example.com", "dunning", "de");
	}

}
