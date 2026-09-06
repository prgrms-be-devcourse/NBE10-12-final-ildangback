package com.gommit.domain.checkin.controller;

import com.gommit.domain.checkin.dto.response.DailyLogCursorResponse;
import com.gommit.domain.checkin.dto.response.DailyLogResponse;
import com.gommit.domain.checkin.service.DailyLogService;
import com.gommit.global.security.CurrentUser;
import com.gommit.global.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "DailyLog", description = "일일로그 조회 API")
@RestController
@RequestMapping("/api/challenges/{challengeId}/daily-logs")
@RequiredArgsConstructor
@Validated
public class DailyLogController {

    private final DailyLogService dailyLogService;

    @Operation(summary = "일일로그 목록 조회 (무한스크롤)")
    @GetMapping
    public ResponseEntity<DailyLogCursorResponse> getDailyLogs(
            @CurrentUser SecurityUser actor,
            @PathVariable Long challengeId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(dailyLogService.getDailyLogs(actor.getId(), challengeId, cursor, size));
    }

    @Operation(summary = "일일로그 단건 조회")
    @GetMapping("/{date}")
    public ResponseEntity<DailyLogResponse> getDailyLog(
            @CurrentUser SecurityUser actor,
            @PathVariable Long challengeId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(dailyLogService.getDailyLog(actor.getId(), challengeId, date));
    }
}
