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

// 챌린지 진행 중 30일마다(시작일 기준 롤링, 달력 월 아님) 발행되는 그룹 단위 체크포인트.
// 한 번 발행되면 이후 원본 데이터(체크인 등)가 바뀌어도 여기 저장된 값은 안 바뀐다.
@Entity
@Getter
@Table(name = "monthly_merges")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MonthlyMerge extends BaseEntity {

    @Column(nullable = false)
    private Long challengeId;

    @Column(nullable = false)
    private int seqNo;

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

    private MonthlyMerge(
            Long challengeId,
            int seqNo,
            LocalDate periodStart,
            LocalDate periodEnd,
            int totalDays,
            int totalCheckInCount,
            int averageCompletionRate,
            LocalDateTime publishedAt) {
        this.challengeId = challengeId;
        this.seqNo = seqNo;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.totalDays = totalDays;
        this.totalCheckInCount = totalCheckInCount;
        this.averageCompletionRate = averageCompletionRate;
        this.publishedAt = publishedAt;
    }

    public static MonthlyMerge create(
            Long challengeId,
            int seqNo,
            LocalDate periodStart,
            LocalDate periodEnd,
            int totalDays,
            int totalCheckInCount,
            int averageCompletionRate,
            LocalDateTime publishedAt) {
        return new MonthlyMerge(
                challengeId,
                seqNo,
                periodStart,
                periodEnd,
                totalDays,
                totalCheckInCount,
                averageCompletionRate,
                publishedAt);
    }
}
