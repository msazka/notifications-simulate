package com.azka.notifications.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

	Optional<Notification> findByProducerIdAndIdempotencyKey(String producerId, String idempotencyKey);

	List<Notification> findAllByOrderByCreatedAtDesc();

	List<Notification> findByStatusOrderByCreatedAtDesc(NotificationStatus status);

}
