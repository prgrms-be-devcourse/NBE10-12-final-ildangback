package com.gommit.domain.report.dto.request;

import com.gommit.domain.report.entity.Report;
import com.gommit.domain.report.entity.ReportReason;
import com.gommit.domain.report.entity.ReportTargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubmitReportRequest(
        @NotNull(message = "신고 대상 종류는 필수입니다.") ReportTargetType targetType,
        @NotNull(message = "신고 대상은 필수입니다.") Long targetId,
        @NotNull(message = "신고 사유는 필수입니다.") ReportReason reason,

        @Size(max = Report.DETAIL_MAX_LENGTH, message = "상황 설명은 500자를 넘을 수 없습니다.")
        String detail) {}
