package com.gommit.domain.record.entity;

import com.gommit.global.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// MonthlyMerge 1건에 대한 참여자별 결과. earnedPoints는 그 기간 동안 획득한 포인트의
// 스냅샷이다 - user_point_histories 최신 상태를 다시 합산하면 안 되고, 발행 시점 값 그대로 고정.
@Entity
@Getter
@Table(name = "monthly_merge_results")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MonthlyMergeResult extends BaseEntity {

    @Column(nullable = false)
    private Long monthlyMergeId;

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

    // "주간 인증 추이" 그래프용 - "1주,2주,3주,4주" / "7,6,7,6" 처럼 콤마로 구분해 저장한다.
    // CheckIn 도메인 없이는 배치가 계산할 수 없어서 지금은 nullable - 값이 없으면
    // 프론트가 그래프 자체를 안 그린다.
    @Column(length = 100)
    private String checkInTrendLabels;

    @Column(length = 100)
    private String checkInTrendCounts;

    private MonthlyMergeResult(
            Long monthlyMergeId,
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
        this.monthlyMergeId = monthlyMergeId;
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

    public static MonthlyMergeResult of(
            Long monthlyMergeId,
            Long userId,
            int ranking,
            int completionRate,
            int completedDayCount,
            int totalCheckInCount,
            int bestStreakInPeriod,
            int earnedPoints,
            int contributionRate) {
        return new MonthlyMergeResult(
                monthlyMergeId,
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

    public static MonthlyMergeResult of(
            Long monthlyMergeId,
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
        return new MonthlyMergeResult(
                monthlyMergeId,
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
