package com.gommit.domain.record.dto.response;

import com.gommit.domain.record.entity.FinalMerge;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record FinalMergeDetailResponse(
        Long challengeId,
        LocalDate periodStart,
        LocalDate periodEnd,
        int totalDays,
        int totalCheckInCount,
        int averageCompletionRate,
        LocalDateTime publishedAt,
        List<MergeParticipantResponse> participants) {

    public static FinalMergeDetailResponse of(FinalMerge merge, List<MergeParticipantResponse> participants) {
        return new FinalMergeDetailResponse(
                merge.getChallengeId(),
                merge.getPeriodStart(),
                merge.getPeriodEnd(),
                merge.getTotalDays(),
                merge.getTotalCheckInCount(),
                merge.getAverageCompletionRate(),
                merge.getPublishedAt(),
                participants);
    }
}
