package com.gommit.domain.report.dto.response;

import com.gommit.domain.report.entity.Appeal;
import com.gommit.domain.report.entity.AppealStatus;
import java.time.LocalDateTime;

public record AppealDetailResponse(
        Long id,
        Long reportId,
        Long appellantId,
        String content,
        AppealStatus status,
        Long decidedBy,
        LocalDateTime decidedAt,
        LocalDateTime createdAt,
        ReportDetailResponse report) {

    public AppealDetailResponse(Appeal appeal, ReportDetailResponse report) {
        this(
                appeal.getId(),
                appeal.getReportId(),
                appeal.getAppellantId(),
                appeal.getContent(),
                appeal.getStatus(),
                appeal.getDecidedBy(),
                appeal.getDecidedAt(),
                appeal.getCreatedAt(),
                report);
    }
}
