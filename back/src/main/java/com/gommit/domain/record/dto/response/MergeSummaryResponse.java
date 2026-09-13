package com.gommit.domain.record.dto.response;

import com.gommit.domain.record.entity.FinalMerge;
import com.gommit.domain.record.entity.MonthlyMerge;
import java.time.LocalDate;
import java.time.LocalDateTime;

// 챌린지의 머지 목록 화면(월간+최종 함께)에서 쓰는 요약 항목.
public record MergeSummaryResponse(
        MergeType type,
        Long mergeId,
        Integer seqNo,
        LocalDate periodStart,
        LocalDate periodEnd,
        int totalDays,
        int totalCheckInCount,
        int averageCompletionRate,
        LocalDateTime publishedAt) {

    public static MergeSummaryResponse from(MonthlyMerge merge) {
        return new MergeSummaryResponse(
                MergeType.MONTHLY,
                merge.getId(),
                merge.getSeqNo(),
                merge.getPeriodStart(),
                merge.getPeriodEnd(),
                merge.getTotalDays(),
                merge.getTotalCheckInCount(),
                merge.getAverageCompletionRate(),
                merge.getPublishedAt());
    }

    public static MergeSummaryResponse from(FinalMerge merge) {
        return new MergeSummaryResponse(
                MergeType.FINAL,
                merge.getId(),
                null,
                merge.getPeriodStart(),
                merge.getPeriodEnd(),
                merge.getTotalDays(),
                merge.getTotalCheckInCount(),
                merge.getAverageCompletionRate(),
                merge.getPublishedAt());
    }
}
