package com.gommit.domain.record.entity;

import com.gommit.global.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 챌린지 종료 시 1회만 발행되는 최종 결산. challengeId당 정확히 1행이다.
@Entity
@Getter
@Table(name = "final_merges")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinalMerge extends BaseEntity {

    @Column(nullable = false, unique = true)
    private Long challengeId;

    @Column(nullable = false)
    private LocalDate periodStart;

    @Column(nullable = false)
    private LocalDate periodEnd;

    @Column(nullable = false)
    private int totalDays;

    @Column(nullable = false)
    private int totalCheckInCount;

    @Column(nullable = false)
    private int averageCompletionRate;

    @Column(nullable = false)
    private LocalDateTime publishedAt;

    private FinalMerge(
            Long challengeId,
            LocalDate periodStart,
            LocalDate periodEnd,
            int totalDays,
            int totalCheckInCount,
            int averageCompletionRate,
            LocalDateTime publishedAt) {
        this.challengeId = challengeId;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.totalDays = totalDays;
        this.totalCheckInCount = totalCheckInCount;
        this.averageCompletionRate = averageCompletionRate;
        this.publishedAt = publishedAt;
    }

    public static FinalMerge create(
            Long challengeId,
            LocalDate periodStart,
            LocalDate periodEnd,
            int totalDays,
            int totalCheckInCount,
            int averageCompletionRate,
            LocalDateTime publishedAt) {
        return new FinalMerge(
                challengeId, periodStart, periodEnd, totalDays, totalCheckInCount, averageCompletionRate, publishedAt);
    }
}
