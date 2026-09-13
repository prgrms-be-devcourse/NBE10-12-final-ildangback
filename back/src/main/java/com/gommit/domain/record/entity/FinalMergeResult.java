package com.gommit.domain.record.entity;

import com.gommit.global.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// FinalMerge 1건에 대한 참여자별 결과. MonthlyMergeResult와 필드 구성이 동일하다
// (기간이 챌린지 전체로 넓어졌을 뿐, 의미는 그대로).
@Entity
@Getter
@Table(name = "final_merge_results")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinalMergeResult extends BaseEntity {

    @Column(nullable = false)
    private Long finalMergeId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private int ranking;

    @Column(nullable = false)
    private int completionRate;

    // "며칠을 인증했는지"(완료율의 분자). totalCheckInCount는 하루 여러 번 인증하면
    // 기간 일수를 넘어갈 수 있어서 "N/기간일수"로 보여줄 땐 이 필드를 써야 한다.
    @Column(nullable = false)
    private int completedDayCount;

    @Column(nullable = false)
    private int totalCheckInCount;

    @Column(nullable = false)
    private int bestStreakInPeriod;

    @Column(nullable = false)
    private int earnedPoints;

    @Column(nullable = false)
    private int contributionRate;

    // "월별 인증 추이" 그래프용 - "3월,4월,5월" / "28,59,27" 처럼 콤마로 구분해 저장한다.
    // CheckIn 도메인 없이는 배치가 계산할 수 없어서 지금은 nullable - 값이 없으면
    // 프론트가 그래프 자체를 안 그린다. 챌린지 전체 기간을 월 단위로 나눈 값이라
    // MonthlyMergeResult(100자, 주 단위)보다 길이가 더 필요해 200자로 잡았다.
    @Column(length = 200)
    private String checkInTrendLabels;

    @Column(length = 200)
    private String checkInTrendCounts;

    private FinalMergeResult(
            Long finalMergeId,
            Long userId,
            int ranking,
            int completionRate,
            int completedDayCount,
            int totalCheckInCount,
            int bestStreakInPeriod,
            int earnedPoints,
            int contributionRate,
            String checkInTrendLabels,
            String checkInTrendCounts) {
        this.finalMergeId = finalMergeId;
        this.userId = userId;
        this.ranking = ranking;
        this.completionRate = completionRate;
        this.completedDayCount = completedDayCount;
        this.totalCheckInCount = totalCheckInCount;
        this.bestStreakInPeriod = bestStreakInPeriod;
        this.earnedPoints = earnedPoints;
        this.contributionRate = contributionRate;
        this.checkInTrendLabels = checkInTrendLabels;
        this.checkInTrendCounts = checkInTrendCounts;
    }

    public static FinalMergeResult of(
            Long finalMergeId,
            Long userId,
            int ranking,
            int completionRate,
            int completedDayCount,
            int totalCheckInCount,
            int bestStreakInPeriod,
            int earnedPoints,
            int contributionRate) {
        return new FinalMergeResult(
                finalMergeId,
                userId,
                ranking,
                completionRate,
                completedDayCount,
                totalCheckInCount,
                bestStreakInPeriod,
                earnedPoints,
                contributionRate,
                null,
                null);
    }

    public static FinalMergeResult of(
            Long finalMergeId,
            Long userId,
            int ranking,
            int completionRate,
            int completedDayCount,
            int totalCheckInCount,
            int bestStreakInPeriod,
            int earnedPoints,
            int contributionRate,
            String checkInTrendLabels,
            String checkInTrendCounts) {
        return new FinalMergeResult(
                finalMergeId,
                userId,
                ranking,
                completionRate,
                completedDayCount,
                totalCheckInCount,
                bestStreakInPeriod,
                earnedPoints,
                contributionRate,
                checkInTrendLabels,
                checkInTrendCounts);
    }
}
