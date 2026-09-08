package com.gommit.global.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BusinessClock — 주입된 Clock 기준 현재 시각")
class BusinessClockTest {

    private BusinessClock businessClockAt(LocalDateTime now) {
        return new BusinessClock(Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneId.of("UTC")));
    }

    @Test
    @DisplayName("today() 는 04:00 이전이면 전날 businessDate")
    void todayBeforeCutoff() {
        assertThat(businessClockAt(LocalDateTime.of(2026, 9, 5, 3, 0, 0)).today())
                .isEqualTo(LocalDate.of(2026, 9, 4));
    }

    @Test
    @DisplayName("today() 는 04:00 이후면 당일 businessDate")
    void todayAfterCutoff() {
        assertThat(businessClockAt(LocalDateTime.of(2026, 9, 5, 4, 0, 0)).today())
                .isEqualTo(LocalDate.of(2026, 9, 5));
    }

    @Test
    @DisplayName("now() 는 컷오프 보정 없이 주입된 Clock 의 시각 그대로")
    void nowReturnsRawInjectedTime() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 5, 3, 0, 0);
        assertThat(businessClockAt(now).now()).isEqualTo(now);
    }
}
