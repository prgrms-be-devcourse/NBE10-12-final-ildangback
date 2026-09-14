package com.gommit.domain.report.dto.response;

import com.gommit.domain.report.entity.Report;
import com.gommit.domain.report.entity.ReportReason;
import com.gommit.domain.report.entity.ReportStatus;
import com.gommit.domain.report.entity.ReportTargetType;
import java.time.LocalDateTime;

public record ReportResponse(
        Long id,
        ReportTargetType targetType,
        Long targetId,
        ReportReason reason,
        ReportStatus status,
        LocalDateTime createdAt) {

    public ReportResponse(Report report) {
        this(
                report.getId(),
                report.getTargetType(),
                report.getTargetId(),
                report.getReason(),
                report.getStatus(),
                report.getCreatedAt());
    }
}
