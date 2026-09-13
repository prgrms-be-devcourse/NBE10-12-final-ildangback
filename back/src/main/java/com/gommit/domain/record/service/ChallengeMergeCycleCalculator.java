package com.gommit.domain.record.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

// 챌린지 시작일 기준 30일 롤링 주기 계산. 달력 계산만 하고 체크인 여부는 모른다 -
// "오늘이 몇 번째 회차의 며칠째인지"만 answer한다(완료율 아님).
@Component
public class ChallengeMergeCycleCalculator {

    static final int CYCLE_LENGTH_DAYS = 30;

    public int cycleLengthDays() {
        return CYCLE_LENGTH_DAYS;
    }

    // 전체 30일 사이클 수(마지막 사이클 포함). 180일이면 6.
    public int totalCycleCount(LocalDate startDate, LocalDate endDate) {
        long totalDays = totalDays(startDate, endDate);
        return (int) Math.ceilDiv(totalDays, CYCLE_LENGTH_DAYS);
    }

    // "월간 머지" 개수 - 마지막 사이클은 월간이 아니라 최종 머지로 발행되므로 1을 뺀다.
    // (예: 180일 챌린지 = 6사이클 = 월간 머지 5개 + 최종 머지 1개)
    public int totalMonthlyMergeCount(LocalDate startDate, LocalDate endDate) {
        return Math.max(0, totalCycleCount(startDate, endDate) - 1);
    }

    public long totalDays(LocalDate startDate, LocalDate endDate) {
        return ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    /** completedMergeCount번째까지 이미 발행됐다고 가정할 때, 그 다음(진행 중) 회차의 순번. */
    public int nextSeqNo(int completedMergeCount) {
        return completedMergeCount + 1;
    }

    /** nextSeqNo 회차가 시작하는 날짜. */
    public LocalDate cycleStartDate(LocalDate challengeStartDate, int seqNo) {
        return challengeStartDate.plusDays((long) (seqNo - 1) * CYCLE_LENGTH_DAYS);
    }

    /** today가 그 회차 안에서 며칠째인지. 1~cycleLengthDays 범위로 자른다. */
    public int cycleDayOf(LocalDate cycleStartDate, LocalDate today) {
        long dayIndex = ChronoUnit.DAYS.between(cycleStartDate, today) + 1;
        return (int) Math.max(1, Math.min(dayIndex, CYCLE_LENGTH_DAYS));
    }
}
