package com.gommit.global.time;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 하루 경계 04:00 기준 ("businessDate"). 00:00~03:59 는 전날로 취급한다.
// 순수 변환만 담당한다. "지금"이 필요하면 BusinessClock 을 주입해서 쓴다.
public final class BusinessDateUtil {

    public static final int CUTOFF_HOUR = 4;

    private BusinessDateUtil() {}

    public static LocalDate of(LocalDateTime dateTime) {
        return dateTime.minusHours(CUTOFF_HOUR).toLocalDate();
    }
}
