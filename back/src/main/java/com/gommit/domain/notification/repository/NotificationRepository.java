package com.gommit.domain.notification.repository;

import com.gommit.domain.notification.entity.Notification;
import com.gommit.domain.notification.entity.NotificationType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findAllByUserIdAndReadAtIsNullOrderByCreatedAtDescIdDesc(Long userId);

    boolean existsByUserIdAndTypeAndRefId(Long userId, NotificationType type, Long refId);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndTypeAndRefIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long userId, NotificationType type, Long refId, LocalDateTime startAt, LocalDateTime endAt);
}
