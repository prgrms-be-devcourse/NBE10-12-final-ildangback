package com.gommit.domain.report.service;

import com.gommit.domain.report.dto.request.DecideAppealRequest;
import com.gommit.domain.report.dto.request.SubmitAppealRequest;
import com.gommit.domain.report.dto.response.AppealDetailResponse;
import com.gommit.domain.report.dto.response.AppealResponse;
import com.gommit.domain.report.dto.response.ReportDetailResponse;
import com.gommit.domain.report.entity.Appeal;
import com.gommit.domain.report.entity.AppealStatus;
import com.gommit.domain.report.entity.Penalty;
import com.gommit.domain.report.entity.Report;
import com.gommit.domain.report.repository.AppealRepository;
import com.gommit.global.dto.SliceResponse;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AppealService {

    private final AppealRepository appealRepository;
    private final ReportService reportService;
    private final PenaltyService penaltyService;

    // 이의제기
    @Transactional
    public AppealResponse submit(Long userId, Long reportId, SubmitAppealRequest request) {
        Report report = reportService.getReport(reportId);
        if (!report.isAccepted() || !report.getTargetUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.APPEAL_NOT_ALLOWED);
        }
        if (appealRepository.existsByReportId(reportId)) {
            throw new BusinessException(ErrorCode.APPEAL_ALREADY_EXISTS);
        }
        return new AppealResponse(appealRepository.save(new Appeal(reportId, userId, request.content())));
    }

    // 이의제기 목록 조회
    public SliceResponse<AppealDetailResponse> getAppeals(AppealStatus status, Long cursor, int size) {
        List<Appeal> rows = appealRepository.findPage(status, cursor, PageRequest.of(0, size + 1));
        SliceResponse<Appeal> page = SliceResponse.ofCursor(rows, size, Appeal::getId);
        Map<Long, ReportDetailResponse> reports = reportService.findDetailsByIds(
                page.content().stream().map(Appeal::getReportId).distinct().toList());
        return new SliceResponse<>(
                page.content().stream()
                        .map(appeal -> new AppealDetailResponse(appeal, reports.get(appeal.getReportId())))
                        .toList(),
                page.hasNext(),
                page.nextCursor());
    }

    // 이의제기 판정
    @Transactional
    public AppealDetailResponse decide(Long userId, Long appealId, DecideAppealRequest request) {
        Appeal appeal = appealRepository
                .findByIdWithLock(appealId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPEAL_NOT_FOUND));
        if (!appeal.isPending()) {
            throw new BusinessException(ErrorCode.APPEAL_ALREADY_DECIDED);
        }

        List<Penalty> penalties;
        if (request.accept()) {
            appeal.accept(userId);
            penalties = penaltyService.revokeAllByReport(appeal.getReportId());
        } else {
            appeal.reject(userId);
            penalties = penaltyService.findByReport(appeal.getReportId());
        }
        Report report = reportService.getReport(appeal.getReportId());
        return new AppealDetailResponse(appeal, reportService.toDetailResponse(report, penalties));
    }
}
