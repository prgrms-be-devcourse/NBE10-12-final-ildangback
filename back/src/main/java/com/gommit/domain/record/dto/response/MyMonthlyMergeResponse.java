package com.gommit.domain.record.dto.response;

import com.gommit.domain.record.entity.MonthlyMerge;
import com.gommit.domain.record.entity.MonthlyMergeResult;
import java.time.LocalDate;

// 내 월간 머지 아카이브(프로필) 커서 조회용 - MonthlyMergeResult(내 결과)와 그 부모
// MonthlyMerge(기간/그룹 평균)를 합쳐서 한 항목으로 만든다.
public record MyMonthlyMergeResponse(
        Long id,
        Long challengeId,
        int seqNo,
        LocalDate periodStart,
        LocalDate periodEnd,
        int myRanking,
        int myCompletionRate,
        int myEarnedPoints,
        int groupAverageCompletionRate) {

    public static MyMonthlyMergeResponse of(MonthlyMergeResult result, MonthlyMerge merge) {
        return new MyMonthlyMergeResponse(
                result.getId(),
                merge.getChallengeId(),
                merge.getSeqNo(),
                merge.getPeriodStart(),
                merge.getPeriodEnd(),
                result.getRanking(),
                result.getCompletionRate(),
                result.getEarnedPoints(),
                merge.getAverageCompletionRate());
    }
}
