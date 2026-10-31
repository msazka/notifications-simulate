package com.azka.notifications.notification;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@DataJpaTest
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void sameProducerAndKeyIsRejectedByDatabase() {
        notificationRepository.saveAndFlush(newNotification("billing", "key-1"));
        assertThatThrownBy(() -> notificationRepository.saveAndFlush(newNotification("billing", "key-1")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameKeyFromAnotherProducerIsAllowed() {
        notificationRepository.saveAndFlush(newNotification("billing", "key-2"));
        notificationRepository.saveAndFlush(newNotification("shop", "key-2"));

        assertThat(notificationRepository.count()).isEqualTo(2);
    }

    @Test
    void conditionalUpdateChangeOnlyPending() {
        var id = notificationRepository.saveAndFlush(newNotification("billing", "key-1")).getId();
        int first = notificationRepository.updateStatusIfCurrent(id, NotificationStatus.PENDING, NotificationStatus.DELIVERED);
        int second = notificationRepository.updateStatusIfCurrent(id, NotificationStatus.PENDING, NotificationStatus.BOUNCED);

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(0);
        assertThat(notificationRepository.findById(id).orElseThrow().getStatus()).isEqualTo(NotificationStatus.DELIVERED);
    }

    @Test
    void pagesAreNewestFirstWithoutOverlap() {
        var base = Instant.parse("2026-10-31T10:00:00Z");
        for (int i = 0; i < 5; i++) {
            notificationRepository.saveAndFlush(newNotification("billing", "key-" + i, base.plusSeconds(i)));
        }
        var sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

        var first = notificationRepository.findAll(PageRequest.of(0, 2, sort));
        var second = notificationRepository.findAll(PageRequest.of(1, 2, sort));
        var third = notificationRepository.findAll(PageRequest.of(2, 2, sort));

        assertThat(first.getTotalElements()).isEqualTo(5);
        assertThat(first.getTotalPages()).isEqualTo(3);
        assertThat(keys(first)).isEqualTo(List.of("key-4", "key-3"));
        assertThat(keys(second)).isEqualTo(List.of("key-2", "key-1"));
        assertThat(keys(third)).isEqualTo(List.of("key-0"));
    }

    @Test
    void findByStatusPagesOnlyMatchingRows() {
        var base = Instant.parse("2026-10-31T10:00:00Z");
        for (int i = 0; i < 4; i++) {
            var notification = newNotification("billing", "key-" + i, base.plusSeconds(i));
            if (i % 2 == 0) {
                notification.setStatus(NotificationStatus.DELIVERED);
            }
            notificationRepository.saveAndFlush(notification);
        }
        var sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

        var page = notificationRepository.findByStatus(NotificationStatus.DELIVERED, PageRequest.of(0, 1, sort));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(keys(page)).isEqualTo(List.of("key-2"));
    }

    private static List<String> keys(Page<Notification> page) {
        return page.getContent().stream().map(Notification::getIdempotencyKey).toList();
    }

    private static Notification newNotification(String producerId, String key, Instant createdAt) {
        var notification = newNotification(producerId, key);
        ReflectionTestUtils.setField(notification, "createdAt", createdAt);
        return notification;
    }

    private static Notification newNotification(String producerId, String key) {
        return new Notification(producerId, key, "hash", "max@example", "dunning", "de");
    }

    @Test
    void eventResponseShowsNewStatus() {
        var service = new NotificationService(notificationRepository);
        var id = notificationRepository.saveAndFlush(newNotification("billing", "key-9")).getId();
        var response = service.applyEvent(id, "sent");

        assertThat(response.status()).isEqualTo(NotificationStatus.DELIVERED);
    }
}