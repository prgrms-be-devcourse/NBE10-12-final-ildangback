package com.gommit.domain.challenge.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Challenge.groupCurrentStreakAsOf — 조회 시점 보정 (ChallengeMember.currentStreakAsOf 와 동일 규칙)")
class ChallengeTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 1);
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 10);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);

    private Challenge challenge() {
        return Challenge.builder()
                .groupId(1L)
                .seqNo(1)
                .startDate(START)
                .endDate(START.plusDays(29))
                .frequencyType(FrequencyType.DAILY)
                .frequencyValue(null)
                .daysOfWeek(null)
                .dailyCheckInCount(1)
                .requiredDayCount(30)
                .groupCurrentStreak(0)
                .groupBestStreak(0)
                .allowPhoto(true)
                .build();
    }

    @Test
    @DisplayName("전원 완료 이력이 없으면 0")
    void neverCompleted() {
        assertThat(challenge().groupCurrentStreakAsOf(TODAY, YESTERDAY)).isZero();
    }

    @Test
    @DisplayName("오늘 이미 전원 완료했으면 저장값 그대로")
    void completedToday() {
        Challenge challenge = challenge();
        challenge.completeGroupDay(YESTERDAY, YESTERDAY.minusDays(1));
        challenge.completeGroupDay(TODAY, YESTERDAY);

        assertThat(challenge.groupCurrentStreakAsOf(TODAY, YESTERDAY)).isEqualTo(2);
    }

    @Test
    @DisplayName("직전 인증 대상일까지 전원 완료가 이어졌으면(오늘은 아직) 저장값 그대로")
    void aliveUntilPreviousCheckInDay() {
        Challenge challenge = challenge();
        challenge.completeGroupDay(YESTERDAY, YESTERDAY.minusDays(1));

        assertThat(challenge.groupCurrentStreakAsOf(TODAY, YESTERDAY)).isEqualTo(1);
    }

    @Test
    @DisplayName("직전 인증 대상일보다 이전이 마지막 전원완료면(중간에 빠졌으면) 0")
    void brokenBeforePreviousCheckInDay() {
        Challenge challenge = challenge();
        challenge.completeGroupDay(TODAY.minusDays(3), TODAY.minusDays(4));

        assertThat(challenge.groupCurrentStreakAsOf(TODAY, YESTERDAY)).isZero();
    }

    @Test
    @DisplayName("previousCheckInDay 가 null 이면 0")
    void nullPreviousCheckInDay() {
        Challenge challenge = challenge();
        challenge.completeGroupDay(TODAY.minusDays(3), TODAY.minusDays(4));

        assertThat(challenge.groupCurrentStreakAsOf(TODAY, null)).isZero();
    }
}
