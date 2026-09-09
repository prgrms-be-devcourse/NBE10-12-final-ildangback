package com.gommit.domain.challenge.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.FrequencyType;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MemberStreakCalculator — 완료한 날 집합에서 연속 길이 유도")
class MemberStreakCalculatorTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 1);

    private final MemberStreakCalculator calculator = new MemberStreakCalculator(new ChallengeProgressCalculator());

    private Challenge dailyChallenge(LocalDate startDate) {
        Challenge challenge = Challenge.builder()
                .groupId(1L)
                .seqNo(1)
                .startDate(startDate)
                .endDate(startDate.plusDays(60))
                .frequencyType(FrequencyType.DAILY)
                .frequencyValue(null)
                .daysOfWeek(null)
                .dailyCheckInCount(1)
                .requiredDayCount(61)
                .groupCurrentStreak(0)
                .groupBestStreak(0)
                .allowPhoto(true)
                .build();
        challenge.activate();
        return challenge;
    }

    private Challenge weeklyChallenge() {
        Challenge challenge = Challenge.builder()
                .groupId(1L)
                .seqNo(1)
                .startDate(START) // 2026-09-01 = 화
                .endDate(START.plusDays(60))
                .frequencyType(FrequencyType.DAYS_OF_WEEK)
                .frequencyValue(null)
                .daysOfWeek("MON,WED,FRI")
                .dailyCheckInCount(1)
                .requiredDayCount(20)
                .groupCurrentStreak(0)
                .groupBestStreak(0)
                .allowPhoto(true)
                .build();
        challenge.activate();
        return challenge;
    }

    @Test
    @DisplayName("직전 대상일들이 이어지면 오늘 포함 길이를 센다")
    void consecutiveDaily() {
        Challenge challenge = dailyChallenge(START);
        LocalDate today = LocalDate.of(2026, 9, 10);
        Set<LocalDate> completed =
                Set.of(today, today.minusDays(1), today.minusDays(2), today.minusDays(3), today.minusDays(4));

        assertThat(calculator.currentStreak(challenge, today, completed)).isEqualTo(5);
    }

    @Test
    @DisplayName("직전 대상일이 비면 오늘 하루만 세어 1")
    void gapResetsDaily() {
        Challenge challenge = dailyChallenge(START);
        LocalDate today = LocalDate.of(2026, 9, 10);
        Set<LocalDate> completed = Set.of(today, today.minusDays(3));

        assertThat(calculator.currentStreak(challenge, today, completed)).isEqualTo(1);
    }

    @Test
    @DisplayName("챌린지 시작일이면 직전 대상일이 없어 거기서 멈춘다")
    void stopsAtChallengeStart() {
        LocalDate start = LocalDate.of(2026, 9, 9);
        Challenge challenge = dailyChallenge(start);
        LocalDate today = LocalDate.of(2026, 9, 10);
        Set<LocalDate> completed = Set.of(today, start);

        assertThat(calculator.currentStreak(challenge, today, completed)).isEqualTo(2);
    }

    @Test
    @DisplayName("요일제 — 달력상 건너뛴 날이 아니라 대상일(월/수/금) 기준으로 연속을 센다")
    void consecutiveByScheduledDays() {
        Challenge challenge = weeklyChallenge();
        LocalDate friday = LocalDate.of(2026, 9, 11); // 금
        Set<LocalDate> completed = Set.of(
                friday, // 금
                LocalDate.of(2026, 9, 9), // 수
                LocalDate.of(2026, 9, 7), // 월
                LocalDate.of(2026, 9, 4)); // 금

        assertThat(calculator.currentStreak(challenge, friday, completed)).isEqualTo(4);
    }
}
