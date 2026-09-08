package com.gommit.global.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BusinessDayCutoff — 하루 경계 04:00 기준")
class BusinessDayCutoffTest {

    @Test
    @DisplayName("04:00 이전이면 전날로 친다")
    void beforeCutoffBelongsToPreviousDay() {
        LocalDate result = BusinessDayCutoff.of(LocalDateTime.of(2026, 9, 5, 3, 59, 59));
        assertThat(result).isEqualTo(LocalDate.of(2026, 9, 4));
    }

    @Test
    @DisplayName("04:00 정각부터는 당일로 친다")
    void atCutoffBelongsToSameDay() {
        LocalDate result = BusinessDayCutoff.of(LocalDateTime.of(2026, 9, 5, 4, 0, 0));
        assertThat(result).isEqualTo(LocalDate.of(2026, 9, 5));
    }

    @Test
    @DisplayName("낮 시간이면 그냥 당일")
    void midDayBelongsToSameDay() {
        LocalDate result = BusinessDayCutoff.of(LocalDateTime.of(2026, 9, 5, 14, 0, 0));
        assertThat(result).isEqualTo(LocalDate.of(2026, 9, 5));
    }

    @Test
    @DisplayName("월 경계도 정상 처리한다")
    void handlesMonthBoundary() {
        LocalDate result = BusinessDayCutoff.of(LocalDateTime.of(2026, 9, 1, 2, 0, 0));
        assertThat(result).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    @DisplayName("firstDayOfBusinessMonth — 1일 04:00 이전이면 지난달 1일")
    void firstDayOfBusinessMonthBeforeCutoff() {
        assertThat(BusinessDayCutoff.firstDayOfBusinessMonth(LocalDateTime.of(2026, 9, 1, 3, 59, 59)))
                .isEqualTo(LocalDate.of(2026, 8, 1));
    }

    @Test
    @DisplayName("firstDayOfBusinessMonth — 1일 04:00 정각부터는 이번달 1일")
    void firstDayOfBusinessMonthAtCutoff() {
        assertThat(BusinessDayCutoff.firstDayOfBusinessMonth(LocalDateTime.of(2026, 9, 1, 4, 0, 0)))
                .isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    @DisplayName("firstDayOfBusinessMonth — 월 중간이면 그냥 이번달 1일")
    void firstDayOfBusinessMonthMidMonth() {
        assertThat(BusinessDayCutoff.firstDayOfBusinessMonth(LocalDateTime.of(2026, 9, 15, 14, 0, 0)))
                .isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    @DisplayName("firstDayOfBusinessMonth — 연 경계도 정상 처리")
    void firstDayOfBusinessMonthYearBoundary() {
        assertThat(BusinessDayCutoff.firstDayOfBusinessMonth(LocalDateTime.of(2026, 1, 1, 1, 0, 0)))
                .isEqualTo(LocalDate.of(2025, 12, 1));
    }

    @Test
    @DisplayName("startTimeOfBusinessDate — 해당 businessDate 의 04:00")
    void startTimeOfBusinessDate() {
        assertThat(BusinessDayCutoff.startTimeOfBusinessDate(LocalDate.of(2026, 9, 5)))
                .isEqualTo(LocalDateTime.of(2026, 9, 5, 4, 0));
    }
}
