package com.gommit.domain.point.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("영업월(새벽 4시 기준) 경계 계산")
class PointPeriodCalculatorTest {

    private final PointPeriodCalculator calculator = new PointPeriodCalculator();

    @Test
    @DisplayName("1일 04:00 이전이면 지난달로 친다")
    void beforeCutoffOnFirstDayBelongsToLastMonth() {
        LocalDate result = calculator.businessMonthFirstDay(LocalDateTime.of(2026, 9, 1, 3, 59, 59));
        assertThat(result).isEqualTo(LocalDate.of(2026, 8, 1));
    }

    @Test
    @DisplayName("1일 04:00 정각부터는 이번달로 친다")
    void atCutoffOnFirstDayBelongsToThisMonth() {
        LocalDate result = calculator.businessMonthFirstDay(LocalDateTime.of(2026, 9, 1, 4, 0, 0));
        assertThat(result).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    @DisplayName("월 중간이면 그냥 이번달 1일")
    void midMonthBelongsToThisMonth() {
        LocalDate result = calculator.businessMonthFirstDay(LocalDateTime.of(2026, 9, 15, 14, 0, 0));
        assertThat(result).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    @DisplayName("연도가 바뀌는 경계도 정상 처리한다")
    void handlesYearBoundary() {
        LocalDate result = calculator.businessMonthFirstDay(LocalDateTime.of(2026, 1, 1, 1, 0, 0));
        assertThat(result).isEqualTo(LocalDate.of(2025, 12, 1));
    }
}
