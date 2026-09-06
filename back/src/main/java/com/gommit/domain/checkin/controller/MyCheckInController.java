package com.gommit.domain.checkin.controller;

import com.gommit.domain.checkin.dto.response.MyCheckInCursorResponse;
import com.gommit.domain.checkin.entity.CheckInType;
import com.gommit.domain.checkin.service.CheckInService;
import com.gommit.global.security.CurrentUser;
import com.gommit.global.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "MyCheckIn", description = "내 인증 모아보기 API")
@RestController
@RequestMapping("/api/users/me/check-ins")
@RequiredArgsConstructor
@Validated
public class MyCheckInController {

    private final CheckInService checkInService;

    @Operation(summary = "내 인증 모아보기 (무한스크롤)")
    @GetMapping
    public MyCheckInCursorResponse getMyCheckIns(
            @CurrentUser SecurityUser actor,
            @RequestParam(required = false) Long challengeId,
            @RequestParam(required = false) CheckInType checkInType,
            @RequestParam(required = false)
                    @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "yyyy-MM 형식이어야 합니다.")
                    String month,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        YearMonth yearMonth = (month == null) ? null : YearMonth.parse(month);
        return checkInService.getMyCheckIns(actor.getId(), challengeId, checkInType, yearMonth, cursor, size);
    }
}
