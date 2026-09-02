package com.gommit.domain.point.controller;

import com.gommit.domain.point.dto.request.PeriodFilter;
import com.gommit.domain.point.dto.request.PointChangeType;
import com.gommit.domain.point.dto.response.PointBalanceResponse;
import com.gommit.domain.point.dto.response.UserPointHistoryResponse;
import com.gommit.domain.point.entity.UserPointReason;
import com.gommit.domain.point.service.PointService;
import com.gommit.global.dto.SliceResponse;
import com.gommit.global.security.CurrentUser;
import com.gommit.global.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
public class PersonalPointController {

    private static final int DEFAULT_SIZE = 20;

    private final PointService pointService;

    @Operation(summary = "개인 포인트 잔액 조회", description = "현재 잔액, 이번 달 적립/사용, 누적 적립을 함께 반환한다.")
    @Tag(name = "Personal Point", description = "개인 포인트 잔액 및 변동 이력 조회")
    @GetMapping("/api/users/me/points")
    public PointBalanceResponse getMyPointBalance(@CurrentUser SecurityUser user) {
        return pointService.getMyBalance(user.getId());
    }

    @Operation(summary = "개인 포인트 이력 조회", description = "최근 발생한 순서로 이력을 커서 기반 무한스크롤로 반환한다.")
    @Tag(name = "Personal Point")
    @GetMapping("/api/users/me/points/histories")
    public SliceResponse<UserPointHistoryResponse> getMyPointHistories(
            @CurrentUser SecurityUser user,
            @Parameter(description = "조회 기간 프리셋") @RequestParam(required = false) PeriodFilter period,
            @Parameter(description = "적립/차감 구분 필터") @RequestParam(required = false) PointChangeType type,
            @Parameter(description = "조회할 변동 사유") @RequestParam(required = false) UserPointReason reason,
            @Parameter(description = "이전 응답의 nextCursor 값. 첫 페이지는 생략") @RequestParam(required = false) Long cursor,
            @Parameter(description = "한 번에 가져올 개수") @RequestParam(defaultValue = "" + DEFAULT_SIZE) @Min(1) @Max(100)
                    int size) {
        return pointService.getMyHistories(user.getId(), period, type, reason, cursor, size);
    }

    @Operation(summary = "개인 포인트 이력 상세 조회", description = "이력 목록에서 항목을 눌렀을 때 보여주는 상세 화면용 API.")
    @Tag(name = "Personal Point")
    @GetMapping("/api/users/me/points/histories/{historyId}")
    public UserPointHistoryResponse getMyPointHistoryDetail(
            @CurrentUser SecurityUser user, @Parameter(description = "조회할 개인 포인트 이력 ID") @PathVariable Long historyId) {
        return pointService.getMyHistoryDetail(user.getId(), historyId);
    }
}
