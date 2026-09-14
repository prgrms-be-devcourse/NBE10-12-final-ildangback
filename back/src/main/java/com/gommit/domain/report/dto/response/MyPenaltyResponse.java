package com.gommit.domain.report.dto.response;

import com.gommit.domain.report.entity.Appeal;
import com.gommit.domain.report.entity.Penalty;
import com.gommit.domain.report.entity.Report;
import com.gommit.domain.report.entity.ReportReason;
import java.time.LocalDateTime;
import java.util.List;

public record MyPenaltyResponse(
        Long reportId,
        ReportReason reason,
        String detail,
        LocalDateTime decidedAt,
        List<PenaltyResponse> penalties,
        AppealResponse appeal,
        boolean appealable) {

    public MyPenaltyResponse(Report report, List<Penalty> penalties, Appeal appeal) {
        this(
                report.getId(),
                report.getReason(),
                report.getDetail(),
                report.getDecidedAt(),
                penalties.stream().map(PenaltyResponse::new).toList(),
                appeal == null ? null : new AppealResponse(appeal),
                appeal == null);
    }
}
