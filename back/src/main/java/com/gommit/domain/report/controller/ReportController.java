package com.gommit.domain.report.controller;

import com.gommit.domain.report.dto.request.DecideAppealRequest;
import com.gommit.domain.report.dto.request.DecideReportRequest;
import com.gommit.domain.report.dto.request.DirectPenaltyRequest;
import com.gommit.domain.report.dto.request.SubmitAppealRequest;
import com.gommit.domain.report.dto.request.SubmitReportRequest;
import com.gommit.domain.report.dto.response.AppealDetailResponse;
import com.gommit.domain.report.dto.response.AppealResponse;
import com.gommit.domain.report.dto.response.MyPenaltyResponse;
import com.gommit.domain.report.dto.response.ReportDetailResponse;
import com.gommit.domain.report.dto.response.ReportResponse;
import com.gommit.domain.report.entity.AppealStatus;
import com.gommit.domain.report.entity.ReportStatus;
import com.gommit.domain.report.service.AppealService;
import com.gommit.domain.report.service.ReportService;
import com.gommit.global.dto.SliceResponse;
import com.gommit.global.security.CurrentUser;
import com.gommit.global.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Reports", description = "신고, 이의제기, 관리자 심사와 제재 API")
@Validated
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final AppealService appealService;

    @Operation(summary = "신고")
    @PostMapping("/reports")
    public ResponseEntity<ReportResponse> submitReport(
            @CurrentUser SecurityUser actor, @Valid @RequestBody SubmitReportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reportService.submit(actor.getId(), request));
    }

    @Operation(summary = "이의제기")
    @PostMapping("/reports/{reportId}/appeals")
    public ResponseEntity<AppealResponse> submitAppeal(
            @CurrentUser SecurityUser actor,
            @PathVariable Long reportId,
            @Valid @RequestBody SubmitAppealRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(appealService.submit(actor.getId(), reportId, request));
    }

    @Operation(summary = "내 제재 목록 조회")
    @GetMapping("/penalties/me")
    public ResponseEntity<SliceResponse<MyPenaltyResponse>> getMyPenalties(
            @CurrentUser SecurityUser actor,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(reportService.getMyPenalties(actor.getId(), cursor, size));
    }

    @Operation(summary = "신고 목록 조회 (관리자)")
    @GetMapping("/admin/reports")
    public ResponseEntity<SliceResponse<ReportDetailResponse>> getReports(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(reportService.getReports(status, cursor, size));
    }

    @Operation(summary = "신고 판정 (관리자)")
    @PatchMapping("/admin/reports/{reportId}")
    public ResponseEntity<ReportDetailResponse> decideReport(
            @CurrentUser SecurityUser actor,
            @PathVariable Long reportId,
            @Valid @RequestBody DecideReportRequest request) {
        return ResponseEntity.ok(reportService.decide(actor.getId(), reportId, request));
    }

    @Operation(summary = "이의제기 목록 조회 (관리자)")
    @GetMapping("/admin/appeals")
    public ResponseEntity<SliceResponse<AppealDetailResponse>> getAppeals(
            @RequestParam(required = false) AppealStatus status,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(appealService.getAppeals(status, cursor, size));
    }

    @Operation(summary = "이의제기 판정 (관리자)")
    @PatchMapping("/admin/appeals/{appealId}")
    public ResponseEntity<AppealDetailResponse> decideAppeal(
            @CurrentUser SecurityUser actor,
            @PathVariable Long appealId,
            @Valid @RequestBody DecideAppealRequest request) {
        return ResponseEntity.ok(appealService.decide(actor.getId(), appealId, request));
    }

    @Operation(summary = "신고 없이 제재 (관리자)")
    @PostMapping("/admin/penalties")
    public ResponseEntity<ReportDetailResponse> penalizeDirectly(
            @CurrentUser SecurityUser actor, @Valid @RequestBody DirectPenaltyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reportService.penalizeDirectly(actor.getId(), request));
    }
}
