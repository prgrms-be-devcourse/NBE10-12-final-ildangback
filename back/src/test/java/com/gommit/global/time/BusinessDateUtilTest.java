package com.gommit.global.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BusinessDateUtil — 하루 경계 04:00 기준")
class BusinessDateUtilTest {

    @Test
    @DisplayName("04:00 이전이면 전날로 친다")
    void beforeCutoffBelongsToPreviousDay() {
        LocalDate result = BusinessDateUtil.of(LocalDateTime.of(2026, 9, 5, 3, 59, 59));
        assertThat(result).isEqualTo(LocalDate.of(2026, 9, 4));
    }

    @Test
    @DisplayName("04:00 정각부터는 당일로 친다")
    void atCutoffBelongsToSameDay() {
        LocalDate result = BusinessDateUtil.of(LocalDateTime.of(2026, 9, 5, 4, 0, 0));
        assertThat(result).isEqualTo(LocalDate.of(2026, 9, 5));
    }

    @Test
    @DisplayName("낮 시간이면 그냥 당일")
    void midDayBelongsToSameDay() {
        LocalDate result = BusinessDateUtil.of(LocalDateTime.of(2026, 9, 5, 14, 0, 0));
        assertThat(result).isEqualTo(LocalDate.of(2026, 9, 5));
    }

    @Test
    @DisplayName("월 경계도 정상 처리한다")
    void handlesMonthBoundary() {
        LocalDate result = BusinessDateUtil.of(LocalDateTime.of(2026, 9, 1, 2, 0, 0));
        assertThat(result).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    @DisplayName("today(Clock) 는 주입된 Clock 을 그대로 따른다")
    void todayUsesInjectedClock() {
        Clock clock = Clock.fixed(LocalDateTime.of(2026, 9, 5, 3, 0, 0).toInstant(ZoneOffset.UTC), ZoneId.of("UTC"));
        assertThat(BusinessDateUtil.today(clock)).isEqualTo(LocalDate.of(2026, 9, 4));
    }
}
