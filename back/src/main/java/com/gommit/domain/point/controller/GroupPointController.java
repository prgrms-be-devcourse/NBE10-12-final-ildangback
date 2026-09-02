package com.gommit.domain.point.controller;

import com.gommit.domain.point.dto.request.PeriodFilter;
import com.gommit.domain.point.dto.request.PointChangeType;
import com.gommit.domain.point.dto.response.GroupPointBalanceResponse;
import com.gommit.domain.point.dto.response.GroupPointHistoryResponse;
import com.gommit.domain.point.entity.GroupPointReason;
import com.gommit.domain.point.service.PointService;
import com.gommit.global.dto.SliceResponse;
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
public class GroupPointController {

    private static final int DEFAULT_SIZE = 20;

    private final PointService pointService;

    @Operation(summary = "그룹 포인트 잔액 조회", description = "해당 그룹에 참여 중인 멤버만 조회할 수 있다.")
    @Tag(name = "Group Point", description = "그룹 포인트 잔액 및 변동 이력 조회")
    @GetMapping("/api/groups/{groupId}/points")
    public GroupPointBalanceResponse getGroupPointBalance(
            @Parameter(description = "조회할 그룹 ID") @PathVariable Long groupId) {
        return pointService.getGroupBalance(groupId);
    }

    @Operation(summary = "그룹 포인트 이력 조회", description = "최근 발생한 순서로 이력을 커서 기반 무한스크롤로 반환한다.")
    @Tag(name = "Group Point")
    @GetMapping("/api/groups/{groupId}/points/histories")
    public SliceResponse<GroupPointHistoryResponse> getGroupPointHistories(
            @Parameter(description = "조회할 그룹 ID") @PathVariable Long groupId,
            @Parameter(description = "조회 기간 프리셋") @RequestParam(required = false) PeriodFilter period,
            @Parameter(description = "적립/차감 구분 필터") @RequestParam(required = false) PointChangeType type,
            @Parameter(description = "조회할 변동 사유") @RequestParam(required = false) GroupPointReason reason,
            @Parameter(description = "이전 응답의 nextCursor 값. 첫 페이지는 생략") @RequestParam(required = false) Long cursor,
            @Parameter(description = "한 번에 가져올 개수") @RequestParam(defaultValue = "" + DEFAULT_SIZE) @Min(1) @Max(100)
                    int size) {
        return pointService.getGroupHistories(groupId, period, type, reason, cursor, size);
    }

    @Operation(summary = "그룹 포인트 이력 상세 조회", description = "이력 목록에서 항목을 눌렀을 때 보여주는 상세 화면용 API.")
    @Tag(name = "Group Point")
    @GetMapping("/api/groups/{groupId}/points/histories/{historyId}")
    public GroupPointHistoryResponse getGroupPointHistoryDetail(
            @Parameter(description = "조회할 그룹 ID") @PathVariable Long groupId,
            @Parameter(description = "조회할 그룹 포인트 이력 ID") @PathVariable Long historyId) {
        return pointService.getGroupHistoryDetail(groupId, historyId);
    }
}
