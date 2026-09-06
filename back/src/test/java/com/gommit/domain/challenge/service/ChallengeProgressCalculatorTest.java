package com.gommit.domain.challenge.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gommit.domain.challenge.entity.DaysOfWeek;
import com.gommit.domain.challenge.entity.FrequencyType;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
}
