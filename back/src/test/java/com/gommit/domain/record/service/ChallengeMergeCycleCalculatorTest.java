package com.gommit.domain.record.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ChallengeMergeCycleCalculator - 30일 롤링 주기 계산")
class ChallengeMergeCycleCalculatorTest {

    private final ChallengeMergeCycleCalculator calculator = new ChallengeMergeCycleCalculator();

    @Test
    @DisplayName("180일 챌린지는 총 6사이클이다")
    void totalCycleCountFor180Days() {
        int result = calculator.totalCycleCount(LocalDate.of(2026, 8, 20), LocalDate.of(2027, 2, 15));
        assertThat(result).isEqualTo(6);
    }

    @Test
    @DisplayName("30일에 딱 안 나누어떨어지면 올림 처리한다")
    void totalCycleCountRoundsUp() {
        // 31일짜리 챌린지 -> 2사이클(1~30일, 31일)
        int result = calculator.totalCycleCount(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        assertThat(result).isEqualTo(2);
    }

    @Test
    @DisplayName("180일 챌린지는 마지막 사이클이 최종 머지로 빠져서 월간 머지는 5개다")
    void totalMonthlyMergeCountExcludesLastCycle() {
        int result = calculator.totalMonthlyMergeCount(LocalDate.of(2026, 8, 20), LocalDate.of(2027, 2, 15));
        assertThat(result).isEqualTo(5);
    }

    @Test
    @DisplayName("3번째 회차는 시작일로부터 60일 뒤에 시작한다")
    void cycleStartDateForThirdSeq() {
        LocalDate start = calculator.cycleStartDate(LocalDate.of(2026, 8, 20), 3);
        assertThat(start).isEqualTo(LocalDate.of(2026, 10, 19));
    }

    @Test
    @DisplayName("회차 시작일로부터 20일째면 DAY 20이다")
    void cycleDayOfMidCycle() {
        int day = calculator.cycleDayOf(LocalDate.of(2026, 11, 18), LocalDate.of(2026, 12, 7));
        assertThat(day).isEqualTo(20);
    }

    @Test
    @DisplayName("회차 범위를 넘어서면 최대값(30)으로 잘린다")
    void cycleDayOfCapsAtCycleLength() {
        int day = calculator.cycleDayOf(LocalDate.of(2026, 11, 18), LocalDate.of(2027, 1, 1));
        assertThat(day).isEqualTo(30);
    }

    @Test
    @DisplayName("완료된 회차가 3개면 다음 회차는 4다")
    void nextSeqNoAfterThreeCompleted() {
        assertThat(calculator.nextSeqNo(3)).isEqualTo(4);
    }
}
