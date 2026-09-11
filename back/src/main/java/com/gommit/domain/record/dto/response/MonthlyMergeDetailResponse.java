package com.gommit.domain.record.dto.response;

import com.gommit.domain.record.entity.MonthlyMerge;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record MonthlyMergeDetailResponse(
        Long challengeId,
        int seqNo,
        LocalDate periodStart,
        LocalDate periodEnd,
        int totalDays,
        int totalCheckInCount,
        int averageCompletionRate,
        LocalDateTime publishedAt,
        List<MergeParticipantResponse> participants) {

    public static MonthlyMergeDetailResponse of(MonthlyMerge merge, List<MergeParticipantResponse> participants) {
        return new MonthlyMergeDetailResponse(
                merge.getChallengeId(),
                merge.getSeqNo(),
                merge.getPeriodStart(),
                merge.getPeriodEnd(),
                merge.getTotalDays(),
                merge.getTotalCheckInCount(),
                merge.getAverageCompletionRate(),
                merge.getPublishedAt(),
                participants);
    }
}
