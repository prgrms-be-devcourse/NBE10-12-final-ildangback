package com.gommit.domain.challenge.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.DaysOfWeek;
import com.gommit.domain.challenge.entity.FrequencyType;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ChallengeProgressCalculatorTest {
    private final ChallengeProgressCalculator calculator = new ChallengeProgressCalculator();

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
}
