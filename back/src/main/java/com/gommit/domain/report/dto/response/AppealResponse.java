package com.gommit.domain.report.dto.response;

import com.gommit.domain.report.entity.Appeal;
import com.gommit.domain.report.entity.AppealStatus;
import java.time.LocalDateTime;

public record AppealResponse(Long id, Long reportId, String content, AppealStatus status, LocalDateTime createdAt) {

    public AppealResponse(Appeal appeal) {
        this(appeal.getId(), appeal.getReportId(), appeal.getContent(), appeal.getStatus(), appeal.getCreatedAt());
    }
}
