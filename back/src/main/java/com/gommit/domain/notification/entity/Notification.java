package com.gommit.domain.notification.entity;

import com.gommit.global.base.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "notifications",
    indexes = {
        @Index(
            name = "idx_notifications_user",
            columnList = "user_id, read_at, created_at"
        )
    }
)
public class Notification extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 255)
    private String body;

    @Column(name = "ref_id")
    private Long refId;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    public Notification(
            Long userId,
            NotificationType type,
            String title,
            String body,
            Long refId
    ) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.refId = refId;
    }

    public void read() {
        if (this.readAt == null) {
            this.readAt = LocalDateTime.now();
        }
    }
}
