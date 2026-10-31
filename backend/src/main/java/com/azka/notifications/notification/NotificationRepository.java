package com.azka.notifications.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

	Optional<Notification> findByProducerIdAndIdempotencyKey(String producerId, String idempotencyKey);

	List<Notification> findAllByOrderByCreatedAtDesc();

	List<Notification> findByStatusOrderByCreatedAtDesc(NotificationStatus status);

	@Modifying(clearAutomatically = true)
	@Query("update Notification n set n.status = :target where n.id = :id and n.status = :expected")
	int updateStatusIfCurrent(@Param("id") UUID id, @Param("expected") NotificationStatus expected,
							  @Param("target") NotificationStatus target);

}
