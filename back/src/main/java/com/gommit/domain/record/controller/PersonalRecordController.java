package com.gommit.domain.record.controller;

import com.gommit.domain.record.dto.response.ChallengeMergeOverviewResponse;
import com.gommit.domain.record.dto.response.MyMonthlyMergeResponse;
import com.gommit.domain.record.dto.response.PersonalStatsResponse;
import com.gommit.domain.record.service.RecordQueryService;
import com.gommit.global.dto.SliceResponse;
import com.gommit.global.security.CurrentUser;
import com.gommit.global.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/users/me")
@Tag(name = "Personal Record", description = "내 월간 머지 아카이브 조회")
@RequiredArgsConstructor
public class PersonalRecordController {

    private static final int DEFAULT_SIZE = 20;

    private final RecordQueryService recordQueryService;

    @Operation(
            summary = "내 개인 전체 통계 조회",
            description = "요약(참여 챌린지 수·완료일수·포인트·최장 연속·평균 완주율), 월별 추이, 카테고리별 집계를 반환한다. "
                    + "이미 발행된 머지 결과(스냅샷)만 집계하므로 진행 중인 챌린지의 오늘 기록은 반영되지 않는다. "
                    + "from/to를 주면 해당 기간과 겹치는 머지 회차만 집계한다(생략하면 전체 기간).")
    @GetMapping("/stats")
    public PersonalStatsResponse getMyStats(
            @CurrentUser SecurityUser user,
            @Parameter(description = "조회 시작일(포함). 생략하면 제한 없음")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @Parameter(description = "조회 종료일(포함). 생략하면 제한 없음")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to) {
        return recordQueryService.getMyStats(user.getId(), from, to);
    }

    @Operation(summary = "내가 속한 챌린지별 머지 진행 현황", description = "월간 머지 아카이브 최상위 화면(챌린지 목록 + 진행률)에서 쓴다.")
    @GetMapping("/challenge-merge-overviews")
    public List<ChallengeMergeOverviewResponse> getMyChallengeMergeOverviews(@CurrentUser SecurityUser user) {
        return recordQueryService.getMyChallengeMergeOverviews(user.getId());
    }

    @Operation(summary = "내 월간 머지 아카이브 조회", description = "내가 참여한 챌린지들의 월간 머지 결과를 최근 발생 순서로 커서 기반 무한스크롤로 반환한다.")
    @GetMapping("/monthly-merges")
    public SliceResponse<MyMonthlyMergeResponse> getMyMonthlyMergeArchive(
            @CurrentUser SecurityUser user,
            @Parameter(description = "이전 응답의 nextCursor 값. 첫 페이지는 생략") @RequestParam(required = false) Long cursor,
            @Parameter(description = "한 번에 가져올 개수") @RequestParam(defaultValue = "" + DEFAULT_SIZE) @Min(1) @Max(100)
                    int size) {
        return recordQueryService.getMyMonthlyMergeArchive(user.getId(), cursor, size);
    }
}
