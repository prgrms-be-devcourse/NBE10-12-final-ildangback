package com.gommit.domain.report.entity;

import com.gommit.global.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "report_appeals")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Appeal extends BaseEntity {

    public static final int CONTENT_MAX_LENGTH = 1000;

    @Column(nullable = false)
    private Long reportId;

    @Column(nullable = false)
    private Long appellantId;

    @Column(nullable = false, length = CONTENT_MAX_LENGTH)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppealStatus status;

    private Long decidedBy;

    private LocalDateTime decidedAt;

    public Appeal(Long reportId, Long appellantId, String content) {
        this.reportId = reportId;
        this.appellantId = appellantId;
        this.content = content;
        this.status = AppealStatus.PENDING;
    }

    public void accept(Long deciderId) {
        decide(AppealStatus.ACCEPTED, deciderId);
    }

    public void reject(Long deciderId) {
        decide(AppealStatus.REJECTED, deciderId);
    }

    public boolean isPending() {
        return this.status == AppealStatus.PENDING;
    }

    private void decide(AppealStatus status, Long deciderId) {
        this.status = status;
        this.decidedBy = deciderId;
        this.decidedAt = LocalDateTime.now();
    }
}
