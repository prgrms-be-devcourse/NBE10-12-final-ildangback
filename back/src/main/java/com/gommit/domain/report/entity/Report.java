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
@Table(name = "reports")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report extends BaseEntity {

    public static final int REPORTED_CONTENT_MAX_LENGTH = 1000;
    public static final int DETAIL_MAX_LENGTH = 500;

    @Column(nullable = false)
    private Long reporterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportTargetType targetType;

    @Column(nullable = false)
    private Long targetId;

    @Column(nullable = false)
    private Long targetUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportReason reason;

    @Column(length = REPORTED_CONTENT_MAX_LENGTH)
    private String reportedContent;

    @Column(length = DETAIL_MAX_LENGTH)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status;

    private Long decidedBy;

    private LocalDateTime decidedAt;

    public Report(
            Long reporterId,
            ReportTargetType targetType,
            Long targetId,
            Long targetUserId,
            ReportReason reason,
            String reportedContent,
            String detail) {
        this.reporterId = reporterId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.targetUserId = targetUserId;
        this.reason = reason;
        this.reportedContent = reportedContent;
        this.detail = detail;
        this.status = ReportStatus.PENDING;
    }

    public static Report byAdmin(Long adminId, Long targetUserId, String detail) {
        Report report =
                new Report(adminId, ReportTargetType.USER, targetUserId, targetUserId, ReportReason.ETC, null, detail);
        report.accept(adminId);
        return report;
    }

    public void accept(Long deciderId) {
        this.status = ReportStatus.ACCEPTED;
        this.decidedBy = deciderId;
        this.decidedAt = LocalDateTime.now();
    }

    public void reject(Long deciderId) {
        this.status = ReportStatus.REJECTED;
        this.decidedBy = deciderId;
        this.decidedAt = LocalDateTime.now();
    }

    public boolean isPending() {
        return this.status == ReportStatus.PENDING;
    }

    public boolean isAccepted() {
        return this.status == ReportStatus.ACCEPTED;
    }
}
