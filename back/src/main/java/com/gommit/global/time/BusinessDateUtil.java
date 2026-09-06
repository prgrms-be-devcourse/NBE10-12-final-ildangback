package com.gommit.global.time;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

// 하루 경계 04:00 기준 ("businessDate"). 00:00~03:59 는 전날로 취급한다.
public final class BusinessDateUtil {

    public static final int CUTOFF_HOUR = 4;

    private BusinessDateUtil() {}

    public static LocalDate today(Clock clock) {
        return of(LocalDateTime.now(clock));
    }

    public static LocalDate of(LocalDateTime dateTime) {
        return dateTime.minusHours(CUTOFF_HOUR).toLocalDate();
    }
}
