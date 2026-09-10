package com.gommit.domain.challenge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.FrequencyType;
import com.gommit.global.time.DaysOfWeek;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ChallengeProgressCalculatorTest {
    private final ChallengeProgressCalculator calculator = new ChallengeProgressCalculator();

    private static final LocalDate START = LocalDate.of(2026, 9, 1);

    private Challenge challenge(FrequencyType type, Integer frequencyValue, String daysOfWeek, boolean active) {
        Challenge challenge = Challenge.builder()
                .groupId(12L)
                .seqNo(1)
                .startDate(START)
                .endDate(START.plusDays(29))
                .frequencyType(type)
                .frequencyValue(frequencyValue)
                .daysOfWeek(daysOfWeek)
                .dailyCheckInCount(1)
                .requiredDayCount(10)
                .groupCurrentStreak(0)
                .groupBestStreak(0)
                .allowPhoto(true)
                .build();
        if (active) {
            challenge.activate();
        }
        return challenge;
    }

    private static String enumNameOf(LocalDate date) {
        return DaysOfWeek.getDaysOfWeek(date.getDayOfWeek()).name();
    }

    @ParameterizedTest
    @CsvSource({"2026-09-01,2026-09-30,30", "2026-09-01,2026-09-01,1"})
    @DisplayName("DAILY는 시작일과 종료일을 모두 포함한다")
    void countsDaily(LocalDate start, LocalDate end, int expected) {
        assertThat(calculator.calculateRequiredDayCount(start, end, FrequencyType.DAILY, null, null))
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "2026-09-01,2026-09-30,5",
        "2026-10-01,2026-10-30,4",
        "2026-09-02,2026-09-02,1",
        "2026-09-03,2026-09-03,0"
    })
    @DisplayName("DAYS_OF_WEEK는 기간에 포함된 수요일만 계산한다")
    void countsWednesdays(LocalDate start, LocalDate end, int expected) {
        assertThat(calculator.calculateRequiredDayCount(
                        start, end, FrequencyType.DAYS_OF_WEEK, null, List.of(DaysOfWeek.WED)))
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "2026-09-01,2026-09-01,3,1",
        "2026-09-01,2026-09-07,3,3",
        "2026-09-01,2026-09-08,3,3",
        "2026-09-01,2026-09-30,2,15"
    })
    @DisplayName("EVERY_N_DAYS는 시작일부터 N일 간격으로 계산한다")
    void countsEveryNDays(LocalDate start, LocalDate end, int interval, int expected) {
        assertThat(calculator.calculateRequiredDayCount(start, end, FrequencyType.EVERY_N_DAYS, interval, null))
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    @DisplayName("ACTIVE DAYS_OF_WEEK 챌린지의 요일이 null 또는 blank이면 현재 인증일 수는 0이다")
    void givenNullOrBlankDaysOfWeekWhenCalculateCurrentDayThenReturnsZero(String daysOfWeek) {
        // given
        LocalDate start = LocalDate.of(2026, 9, 1);
        Challenge challenge = Challenge.builder()
                .groupId(12L)
                .seqNo(1)
                .startDate(start)
                .endDate(start.plusDays(6))
                .frequencyType(FrequencyType.DAYS_OF_WEEK)
                .daysOfWeek(daysOfWeek)
                .dailyCheckInCount(1)
                .requiredDayCount(7)
                .groupCurrentStreak(0)
                .groupBestStreak(0)
                .allowPhoto(true)
                .build();
        challenge.activate();

        // when
        int currentDay = calculator.calculateCurrentDay(challenge, start.plusDays(3));

        // then
        assertThat(currentDay).isZero();
    }

    @Nested
    @DisplayName("isCheckInDay — 순수 달력 규칙 (챌린지 status 는 보지 않는다)")
    class IsCheckInDay {

        @Test
        @DisplayName("기간 밖(시작 전/종료 후)은 대상일이 아니다")
        void outsideRange() {
            Challenge challenge = challenge(FrequencyType.DAILY, null, null, true);
            assertThat(calculator.isCheckInDay(challenge, START.minusDays(1))).isFalse();
            assertThat(calculator.isCheckInDay(challenge, START.plusDays(30))).isFalse();
        }

        @Test
        @DisplayName("DAILY 는 기간 내 모든 날이 대상일 (시작일 당일 포함)")
        void daily() {
            Challenge challenge = challenge(FrequencyType.DAILY, null, null, true);
            assertThat(calculator.isCheckInDay(challenge, START)).isTrue();
            assertThat(calculator.isCheckInDay(challenge, START.plusDays(15))).isTrue();
        }

        @Test
        @DisplayName("DAYS_OF_WEEK 는 지정 요일만 대상일 (정규 enum명 MON..SUN)")
        void daysOfWeek() {
            LocalDate day = START.plusDays(9);
            Challenge challenge = challenge(FrequencyType.DAYS_OF_WEEK, null, enumNameOf(day), true);
            assertThat(calculator.isCheckInDay(challenge, day)).isTrue();
            assertThat(calculator.isCheckInDay(challenge, day.plusDays(1))).isFalse();
        }

        @Test
        @DisplayName("DAYS_OF_WEEK CSV 중 하나만 맞아도 대상일")
        void daysOfWeekCsv() {
            LocalDate day = START.plusDays(9);
            Challenge challenge = challenge(FrequencyType.DAYS_OF_WEEK, null, "SUN," + enumNameOf(day) + ",WED", true);
            assertThat(calculator.isCheckInDay(challenge, day)).isTrue();
        }

        @Test
        @DisplayName("DAYS_OF_WEEK 토큰이 정규 enum명이 아니면(예: MONDAY) 예외 — 저장 포맷은 항상 MON..SUN")
        void daysOfWeekRejectsNonCanonicalToken() {
            LocalDate day = START.plusDays(9);
            Challenge challenge = challenge(
                    FrequencyType.DAYS_OF_WEEK, null, day.getDayOfWeek().name(), true);
            assertThatThrownBy(() -> calculator.isCheckInDay(challenge, day))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("EVERY_N_DAYS 는 시작일로부터 N일 간격만 대상일 (N=3, 0일차 포함)")
        void everyNDays() {
            Challenge challenge = challenge(FrequencyType.EVERY_N_DAYS, 3, null, true);
            assertThat(calculator.isCheckInDay(challenge, START)).isTrue();
            assertThat(calculator.isCheckInDay(challenge, START.plusDays(1))).isFalse();
            assertThat(calculator.isCheckInDay(challenge, START.plusDays(3))).isTrue();
            assertThat(calculator.isCheckInDay(challenge, START.plusDays(6))).isTrue();
        }

        @Test
        @DisplayName("READY 챌린지여도 스케줄상 맞으면 true (status 무관)")
        void ignoresStatus() {
            Challenge challenge = challenge(FrequencyType.DAILY, null, null, false);
            assertThat(calculator.isCheckInDay(challenge, START.plusDays(5))).isTrue();
        }
    }

    @Nested
    @DisplayName("canCheckInOn — 예정일 + 챌린지 ACTIVE")
    class CanCheckInOn {

        @Test
        @DisplayName("ACTIVE + 예정일 → true")
        void activeAndCheckInDay() {
            Challenge challenge = challenge(FrequencyType.DAILY, null, null, true);
            assertThat(calculator.canCheckInOn(challenge, START.plusDays(5))).isTrue();
        }

        @Test
        @DisplayName("스케줄상 예정일이어도 챌린지가 ACTIVE 가 아니면 false")
        void notActive() {
            Challenge challenge = challenge(FrequencyType.DAILY, null, null, false);
            assertThat(calculator.canCheckInOn(challenge, START.plusDays(5))).isFalse();
        }

        @Test
        @DisplayName("ACTIVE 여도 예정일이 아니면 false")
        void activeButNotCheckInDay() {
            Challenge challenge = challenge(FrequencyType.EVERY_N_DAYS, 3, null, true);
            assertThat(calculator.canCheckInOn(challenge, START.plusDays(1))).isFalse();
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("DAYS_OF_WEEK의 요일 목록이 null 또는 empty이면 전체 인증 예정일 수는 0이다")
    void givenNullOrEmptyDaysOfWeekWhenCalculateRequiredDayCountThenReturnsZero(List<DaysOfWeek> daysOfWeek) {
        // given
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = start.plusDays(6);

        // when
        int requiredDayCount =
                calculator.calculateRequiredDayCount(start, end, FrequencyType.DAYS_OF_WEEK, null, daysOfWeek);

        // then
        assertThat(requiredDayCount).isZero();
    }

    @Nested
    @DisplayName("previousCheckInDay — businessDate 직전의 인증 대상일")
    class PreviousCheckInDay {

        private Challenge challenge(FrequencyType type, Integer frequencyValue, String daysOfWeek) {
            Challenge challenge = Challenge.builder()
                    .groupId(1L)
                    .seqNo(1)
                    .startDate(LocalDate.of(2026, 9, 1))
                    .endDate(LocalDate.of(2026, 9, 30))
                    .frequencyType(type)
                    .frequencyValue(frequencyValue)
                    .daysOfWeek(daysOfWeek)
                    .dailyCheckInCount(1)
                    .requiredDayCount(30)
                    .groupCurrentStreak(0)
                    .groupBestStreak(0)
                    .allowPhoto(true)
                    .build();
            challenge.activate();
            return challenge;
        }

        @Test
        @DisplayName("DAILY 는 전날")
        void daily() {
            assertThat(calculator.previousCheckInDay(
                            challenge(FrequencyType.DAILY, null, null), LocalDate.of(2026, 9, 10)))
                    .isEqualTo(LocalDate.of(2026, 9, 9));
        }

        @Test
        @DisplayName("DAILY — 시작일이면 직전 대상일 없음(null)")
        void dailyOnStart() {
            assertThat(calculator.previousCheckInDay(
                            challenge(FrequencyType.DAILY, null, null), LocalDate.of(2026, 9, 1)))
                    .isNull();
        }

        @Test
        @DisplayName("DAYS_OF_WEEK — 직전 스케줄 요일 (금 다음 인증일의 직전은 수)")
        void daysOfWeek() {
            // 2026-09-11(금)의 직전 스케줄일은 2026-09-09(수)
            Challenge wedFri = challenge(FrequencyType.DAYS_OF_WEEK, null, "WED,FRI");
            assertThat(calculator.previousCheckInDay(wedFri, LocalDate.of(2026, 9, 11)))
                    .isEqualTo(LocalDate.of(2026, 9, 9));
        }

        @Test
        @DisplayName("EVERY_N_DAYS 는 businessDate - N")
        void everyNDays() {
            assertThat(calculator.previousCheckInDay(
                            challenge(FrequencyType.EVERY_N_DAYS, 3, null), LocalDate.of(2026, 9, 10)))
                    .isEqualTo(LocalDate.of(2026, 9, 7));
        }

        @Test
        @DisplayName("EVERY_N_DAYS — 대상일 사이 businessDate 는 직전 대상일로 내림 (N=3, 9/6 → 9/4)")
        void everyNDaysBetweenTargets() {
            assertThat(calculator.previousCheckInDay(
                            challenge(FrequencyType.EVERY_N_DAYS, 3, null), LocalDate.of(2026, 9, 6)))
                    .isEqualTo(LocalDate.of(2026, 9, 4));
        }

        @Test
        @DisplayName("EVERY_N_DAYS — 시작일 다음 날이면 직전은 시작일")
        void everyNDaysStartPlusOne() {
            assertThat(calculator.previousCheckInDay(
                            challenge(FrequencyType.EVERY_N_DAYS, 3, null), LocalDate.of(2026, 9, 2)))
                    .isEqualTo(LocalDate.of(2026, 9, 1));
        }

        @Test
        @DisplayName("DAYS_OF_WEEK — 직전 스케줄일이 여러 날 전 (월요일 businessDate 의 직전은 금)")
        void daysOfWeekMultipleDaysBack() {
            // 2026-09-14(월)의 직전 스케줄일은 2026-09-11(금)
            assertThat(calculator.previousCheckInDay(
                            challenge(FrequencyType.DAYS_OF_WEEK, null, "WED,FRI"), LocalDate.of(2026, 9, 14)))
                    .isEqualTo(LocalDate.of(2026, 9, 11));
        }

        @Test
        @DisplayName("종료일 뒤 businessDate — 종료일까지로 클램프 (DAILY → 종료일)")
        void clampsAfterEndDateDaily() {
            assertThat(calculator.previousCheckInDay(
                            challenge(FrequencyType.DAILY, null, null), LocalDate.of(2026, 10, 5)))
                    .isEqualTo(LocalDate.of(2026, 9, 30));
        }

        @Test
        @DisplayName("종료일 뒤 businessDate — EVERY_N_DAYS 는 종료일 이하 마지막 대상일 (N=7 → 9/29)")
        void clampsAfterEndDateEveryN() {
            assertThat(calculator.previousCheckInDay(
                            challenge(FrequencyType.EVERY_N_DAYS, 7, null), LocalDate.of(2026, 10, 10)))
                    .isEqualTo(LocalDate.of(2026, 9, 29));
        }

        @Test
        @DisplayName("EVERY_N_DAYS 인데 frequencyValue 가 0 이하/null 이면 null (misconfig 방어)")
        void everyNDaysWithInvalidFrequencyValue() {
            assertThat(calculator.previousCheckInDay(
                            challenge(FrequencyType.EVERY_N_DAYS, 0, null), LocalDate.of(2026, 9, 10)))
                    .isNull();
            assertThat(calculator.previousCheckInDay(
                            challenge(FrequencyType.EVERY_N_DAYS, null, null), LocalDate.of(2026, 9, 10)))
                    .isNull();
        }
    }
}
