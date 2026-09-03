package com.gommit.domain.checkin.controller;

import com.gommit.domain.checkin.dto.request.SubmitCheckInForm;
import com.gommit.domain.checkin.dto.response.CheckInCursorResponse;
import com.gommit.domain.checkin.dto.response.CheckInResponse;
import com.gommit.domain.checkin.dto.response.CheckInResultResponse;
import com.gommit.domain.checkin.dto.response.RecentCheckInResponse;
import com.gommit.domain.checkin.dto.response.TodayCheckInStatusResponse;
import com.gommit.domain.checkin.entity.CheckInType;
import com.gommit.domain.checkin.service.CheckInService;
import com.gommit.global.security.CurrentUser;
import com.gommit.global.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "CheckIn", description = "인증 제출/조회 API")
@RestController
@RequestMapping("/api/challenges/{challengeId}/check-ins")
@RequiredArgsConstructor
@Validated
public class CheckInController {

    private final CheckInService checkInService;

    @Operation(summary = "오늘의 인증 상태 조회")
    @GetMapping("/today")
    public ResponseEntity<TodayCheckInStatusResponse> getTodayStatus(
            @CurrentUser SecurityUser actor, @PathVariable Long challengeId) {
        return ResponseEntity.ok(checkInService.getTodayStatus(actor.getId(), challengeId));
    }

    @Operation(summary = "인증 제출")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CheckInResultResponse> submit(
            @CurrentUser SecurityUser actor,
            @PathVariable Long challengeId,
            @RequestParam CheckInType checkInType,
            @RequestParam(required = false) String memo,
            @RequestPart MultipartFile media) {

        CheckInResultResponse body =
                checkInService.submit(actor.getId(), challengeId, new SubmitCheckInForm(checkInType, memo), media);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "인증 기록 목록 조회 (갤러리)")
    @GetMapping
    public ResponseEntity<CheckInCursorResponse> getGallery(
            @CurrentUser SecurityUser actor,
            @PathVariable Long challengeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) CheckInType checkInType,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        return ResponseEntity.ok(
                checkInService.getGallery(actor.getId(), challengeId, date, userId, checkInType, cursor, size));
    }

    @Operation(summary = "최근 인증 로그 한줄보기")
    @GetMapping("/recent")
    public ResponseEntity<RecentCheckInResponse> getRecent(
            @CurrentUser SecurityUser actor,
            @PathVariable Long challengeId,
            @RequestParam(defaultValue = "3") @Min(1) @Max(20) int size) {

        return ResponseEntity.ok(checkInService.getRecent(actor.getId(), challengeId, size));
    }

    @Operation(summary = "인증 단건 조회")
    @GetMapping("/{checkInId}")
    public ResponseEntity<CheckInResponse> getCheckIn(
            @CurrentUser SecurityUser actor, @PathVariable Long challengeId, @PathVariable Long checkInId) {
        return ResponseEntity.ok(checkInService.getCheckIn(actor.getId(), challengeId, checkInId));
    }
}
