package com.gommit.domain.record.controller;

import com.gommit.domain.record.dto.response.ChallengeMergeOverviewResponse;
import com.gommit.domain.record.dto.response.FinalMergeDetailResponse;
import com.gommit.domain.record.dto.response.MergeSummaryResponse;
import com.gommit.domain.record.dto.response.MonthlyMergeDetailResponse;
import com.gommit.domain.record.service.RecordQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/challenges/{challengeId}")
@Tag(name = "Group Record", description = "챌린지 월간/최종 머지 조회")
@RequiredArgsConstructor
public class GroupRecordController {

    private final RecordQueryService recordQueryService;

    @Operation(summary = "머지 진행 현황 조회", description = "월간 머지 목록 화면 상단 요약(발행 회차 수, 진행 중인 회차의 날짜 등)에 쓴다.")
    @GetMapping("/merge-overview")
    public ChallengeMergeOverviewResponse getChallengeMergeOverview(
            @Parameter(description = "조회할 챌린지 ID") @PathVariable Long challengeId) {
        return recordQueryService.getChallengeMergeOverview(challengeId);
    }

    @Operation(summary = "머지 목록 조회", description = "그 챌린지의 최종 머지(있으면)와 월간 머지들을 최신순으로 반환한다.")
    @GetMapping("/merges")
    public List<MergeSummaryResponse> getMergeList(
            @Parameter(description = "조회할 챌린지 ID") @PathVariable Long challengeId) {
        return recordQueryService.getMergeList(challengeId);
    }

    @Operation(summary = "월간 머지 상세 조회")
    @GetMapping("/monthly-merges/{seqNo}")
    public MonthlyMergeDetailResponse getMonthlyMergeDetail(
            @Parameter(description = "조회할 챌린지 ID") @PathVariable Long challengeId,
            @Parameter(description = "챌린지 내 월간 머지 회차") @PathVariable int seqNo) {
        return recordQueryService.getMonthlyMergeDetail(challengeId, seqNo);
    }

    @Operation(summary = "최종 머지 상세 조회")
    @GetMapping("/final-merge")
    public FinalMergeDetailResponse getFinalMergeDetail(
            @Parameter(description = "조회할 챌린지 ID") @PathVariable Long challengeId) {
        return recordQueryService.getFinalMergeDetail(challengeId);
    }
}
