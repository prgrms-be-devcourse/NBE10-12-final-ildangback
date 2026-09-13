package com.gommit.domain.report.service;

import com.gommit.domain.chat.service.ChatService;
import com.gommit.domain.checkin.service.CheckInService;
import com.gommit.domain.report.dto.request.DecideReportRequest;
import com.gommit.domain.report.dto.request.DirectPenaltyRequest;
import com.gommit.domain.report.dto.request.SubmitReportRequest;
import com.gommit.domain.report.dto.response.MyPenaltyResponse;
import com.gommit.domain.report.dto.response.ReportDetailResponse;
import com.gommit.domain.report.dto.response.ReportResponse;
import com.gommit.domain.report.entity.Appeal;
import com.gommit.domain.report.entity.Penalty;
import com.gommit.domain.report.entity.Report;
import com.gommit.domain.report.entity.ReportReason;
import com.gommit.domain.report.entity.ReportStatus;
import com.gommit.domain.report.entity.ReportTargetType;
import com.gommit.domain.report.repository.AppealRepository;
import com.gommit.domain.report.repository.PenaltyRepository;
import com.gommit.domain.report.repository.ReportRepository;
import com.gommit.domain.user.entity.User;
import com.gommit.domain.user.repository.UserRepository;
import com.gommit.domain.user.service.UserService;
import com.gommit.global.dto.SliceResponse;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private static final String USER_CONTENT_FORMAT = "닉네임: %s | 자기소개: %s";

    private final ReportRepository reportRepository;
    private final PenaltyRepository penaltyRepository;
    private final AppealRepository appealRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final PenaltyService penaltyService;
    private final CheckInService checkInService;
    private final ChatService chatService;

    // 신고 접수
    @Transactional
    public ReportResponse submit(Long userId, SubmitReportRequest request) {
        if (request.reason() == ReportReason.ETC && isBlank(request.detail())) {
            throw new BusinessException(ErrorCode.REPORT_DETAIL_REQUIRED);
        }

        Target target = resolveTarget(request.targetType(), request.targetId());
        if (target.targetUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.SELF_REPORT_NOT_ALLOWED);
        }
        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetIdAndStatus(
                userId, request.targetType(), request.targetId(), ReportStatus.PENDING)) {
            throw new BusinessException(ErrorCode.DUPLICATE_PENDING_REPORT);
        }

        Report report = reportRepository.save(new Report(
                userId,
                request.targetType(),
                request.targetId(),
                target.targetUserId(),
                request.reason(),
                target.reportedContent(),
                request.detail()));
        return new ReportResponse(report);
    }

    // 신고 목록 조회
    public SliceResponse<ReportDetailResponse> getReports(ReportStatus status, Long cursor, int size) {
        List<Report> rows = reportRepository.findPage(status, cursor, PageRequest.of(0, size + 1));
        SliceResponse<Report> page = SliceResponse.ofCursor(rows, size, Report::getId);
        return new SliceResponse<>(toDetailResponses(page.content()), page.hasNext(), page.nextCursor());
    }

    // 신고 판정
    @Transactional
    public ReportDetailResponse decide(Long userId, Long reportId, DecideReportRequest request) {
        Report report = getReport(reportId);
        if (!report.isPending()) {
            throw new BusinessException(ErrorCode.REPORT_ALREADY_DECIDED);
        }

        if (!request.accept()) {
            report.reject(userId);
            return toDetailResponse(report, List.of());
        }

        if (request.penaltiesOrEmpty().isEmpty()) {
            throw new BusinessException(ErrorCode.PENALTY_REQUIRED);
        }
        applyContentAction(report);
        report.accept(userId);
        List<Penalty> penalties =
                penaltyService.apply(report.getId(), report.getTargetUserId(), request.penaltiesOrEmpty());
        return toDetailResponse(report, penalties);
    }

    // 신고 없이 거는 제재
    @Transactional
    public ReportDetailResponse penalizeDirectly(Long userId, DirectPenaltyRequest request) {
        User target = userRepository
                .findByEmailAndDeletedAtIsNull(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Report report = reportRepository.save(Report.byAdmin(userId, target.getId(), request.detail()));
        List<Penalty> penalties = penaltyService.apply(report.getId(), target.getId(), request.penalties());
        return toDetailResponse(report, penalties);
    }

    // 내가 받은 제재 목록
    public SliceResponse<MyPenaltyResponse> getMyPenalties(Long userId, Long cursor, int size) {
        List<Report> rows = reportRepository.findPageByTargetUser(userId, cursor, PageRequest.of(0, size + 1));
        SliceResponse<Report> page = SliceResponse.ofCursor(rows, size, Report::getId);
        if (page.content().isEmpty()) {
            return new SliceResponse<>(List.of(), page.hasNext(), page.nextCursor());
        }

        List<Long> reportIds = page.content().stream().map(Report::getId).toList();
        Map<Long, List<Penalty>> penaltiesByReport = penaltyRepository.findAllByReportIdIn(reportIds).stream()
                .collect(Collectors.groupingBy(Penalty::getReportId));
        Map<Long, Appeal> appealsByReport = appealRepository.findAllByReportIdIn(reportIds).stream()
                .collect(Collectors.toMap(Appeal::getReportId, Function.identity()));

        return new SliceResponse<>(
                page.content().stream()
                        .map(report -> new MyPenaltyResponse(
                                report,
                                penaltiesByReport.getOrDefault(report.getId(), List.of()),
                                appealsByReport.get(report.getId())))
                        .toList(),
                page.hasNext(),
                page.nextCursor());
    }

    // 신고 1건 조회
    Report getReport(Long reportId) {
        return reportRepository.findById(reportId).orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
    }

    // 신고 상세 응답 조립
    ReportDetailResponse toDetailResponse(Report report, List<Penalty> penalties) {
        long pastPenaltyCount = penaltyService
                .countActiveByUsers(List.of(report.getTargetUserId()))
                .getOrDefault(report.getTargetUserId(), 0L);
        Map<Long, String> nicknames =
                userService.findNicknames(List.of(report.getTargetUserId(), report.getReporterId()));
        return new ReportDetailResponse(
                report,
                nicknames.get(report.getTargetUserId()),
                nicknames.get(report.getReporterId()),
                pastPenaltyCount,
                penalties);
    }

    // 신고 상세 응답 여럿 조립
    Map<Long, ReportDetailResponse> findDetailsByIds(List<Long> reportIds) {
        if (reportIds.isEmpty()) {
            return Map.of();
        }
        List<Report> reports = reportRepository.findAllByIdIn(reportIds);
        Map<Long, Long> counts = penaltyService.countActiveByUsers(
                reports.stream().map(Report::getTargetUserId).distinct().toList());
        Map<Long, List<Penalty>> penaltiesByReport = penaltyRepository.findAllByReportIdIn(reportIds).stream()
                .collect(Collectors.groupingBy(Penalty::getReportId));
        Map<Long, String> nicknames = findNicknames(reports);
        return reports.stream()
                .collect(Collectors.toMap(
                        Report::getId,
                        report -> new ReportDetailResponse(
                                report,
                                nicknames.get(report.getTargetUserId()),
                                nicknames.get(report.getReporterId()),
                                counts.getOrDefault(report.getTargetUserId(), 0L),
                                penaltiesByReport.getOrDefault(report.getId(), List.of()))));
    }

    // 대상 종류별 콘텐츠 조치
    private void applyContentAction(Report report) {
        switch (report.getTargetType()) {
            case USER -> userService.resetProfileOnPenalty(report.getTargetUserId());
            case CHECK_IN -> checkInService.deleteByAdmin(report.getTargetId());
            case CHAT_MESSAGE -> chatService.hideByAdmin(report.getTargetId());
        }
    }

    // 대상에서 제재받을 사용자와 접수 시점 원본 찾기
    private Target resolveTarget(ReportTargetType targetType, Long targetId) {
        return switch (targetType) {
            case USER -> {
                User user = userRepository
                        .findByIdAndDeletedAtIsNull(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                yield new Target(
                        user.getId(),
                        truncate(USER_CONTENT_FORMAT.formatted(user.getNickname(), orEmpty(user.getIntroduction()))));
            }
            case CHECK_IN -> {
                CheckInService.ReportedCheckIn checkIn = checkInService
                        .findForReport(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.CHECK_IN_NOT_FOUND));
                yield new Target(checkIn.userId(), truncate(checkIn.reportedContent()));
            }
            case CHAT_MESSAGE -> {
                ChatService.ReportedMessage message = chatService
                        .findForReport(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_MESSAGE_NOT_FOUND));
                yield new Target(message.senderId(), truncate(message.reportedContent()));
            }
        };
    }

    // 신고 목록 행 조립
    private List<ReportDetailResponse> toDetailResponses(List<Report> reports) {
        if (reports.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> counts = penaltyService.countActiveByUsers(
                reports.stream().map(Report::getTargetUserId).distinct().toList());
        Map<Long, List<Penalty>> penaltiesByReport =
                penaltyRepository
                        .findAllByReportIdIn(reports.stream().map(Report::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(Penalty::getReportId));
        Map<Long, String> nicknames = findNicknames(reports);
        return reports.stream()
                .map(report -> new ReportDetailResponse(
                        report,
                        nicknames.get(report.getTargetUserId()),
                        nicknames.get(report.getReporterId()),
                        counts.getOrDefault(report.getTargetUserId(), 0L),
                        penaltiesByReport.getOrDefault(report.getId(), List.of())))
                .toList();
    }

    // 대상자와 신고자 닉네임
    private Map<Long, String> findNicknames(List<Report> reports) {
        return userService.findNicknames(reports.stream()
                .flatMap(report -> Stream.of(report.getTargetUserId(), report.getReporterId()))
                .distinct()
                .toList());
    }

    private static String truncate(String value) {
        return value.length() <= Report.REPORTED_CONTENT_MAX_LENGTH
                ? value
                : value.substring(0, Report.REPORTED_CONTENT_MAX_LENGTH);
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record Target(Long targetUserId, String reportedContent) {}
}
