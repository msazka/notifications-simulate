package com.azka.notifications.notification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
@DataJpaTest
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void sameProducerAndKeyIsRejectedByDatabase () {
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

    private static  Notification newNotification(String producerId, String key) {
    return new Notification(producerId,key, "hash", "max@example", "dunning", "de");
    }
}