package com.gommit.domain.report.dto.response;

import com.gommit.domain.report.entity.Penalty;
import com.gommit.domain.report.entity.Report;
import com.gommit.domain.report.entity.ReportReason;
import com.gommit.domain.report.entity.ReportStatus;
import com.gommit.domain.report.entity.ReportTargetType;
import java.time.LocalDateTime;
import java.util.List;

public record ReportDetailResponse(
        Long id,
        Long reporterId,
        ReportTargetType targetType,
        Long targetId,
        Long targetUserId,
        String targetUserNickname,
        String reporterNickname,
        ReportReason reason,
        String reportedContent,
        String detail,
        ReportStatus status,
        Long decidedBy,
        LocalDateTime decidedAt,
        LocalDateTime createdAt,
        long pastPenaltyCount,
        List<PenaltyResponse> penalties) {

    public ReportDetailResponse(
            Report report,
            String targetUserNickname,
            String reporterNickname,
            long pastPenaltyCount,
            List<Penalty> penalties) {
        this(
                report.getId(),
                report.getReporterId(),
                report.getTargetType(),
                report.getTargetId(),
                report.getTargetUserId(),
                targetUserNickname,
                reporterNickname,
                report.getReason(),
                report.getReportedContent(),
                report.getDetail(),
                report.getStatus(),
                report.getDecidedBy(),
                report.getDecidedAt(),
                report.getCreatedAt(),
                pastPenaltyCount,
                penalties.stream().map(PenaltyResponse::new).toList());
    }
}
