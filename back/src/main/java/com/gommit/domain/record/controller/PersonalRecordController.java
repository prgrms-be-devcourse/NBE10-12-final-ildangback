package com.gommit.domain.record.controller;

import com.gommit.domain.record.dto.response.ChallengeMergeOverviewResponse;
import com.gommit.domain.record.dto.response.MyMonthlyMergeResponse;
import com.gommit.domain.record.service.RecordQueryService;
import com.gommit.global.dto.SliceResponse;
import com.gommit.global.security.CurrentUser;
import com.gommit.global.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
