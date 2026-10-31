package com.azka.notifications.notification;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

	Optional<Notification> findByProducerIdAndIdempotencyKey(String producerId, String idempotencyKey);

	Page<Notification> findByStatus(NotificationStatus status, Pageable pageable);

	@Modifying(clearAutomatically = true)
	@Query("update Notification n set n.status = :target where n.id = :id and n.status = :expected")
	int updateStatusIfCurrent(@Param("id") UUID id, @Param("expected") NotificationStatus expected,
							  @Param("target") NotificationStatus target);

}
