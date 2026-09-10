package com.gommit.global.time;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 하루 경계 04:00 기준 ("businessDate"). 00:00~03:59 는 전날로 취급한다.
// 순수 변환만 담당한다. "지금"이 필요하면 BusinessClock 을 주입해서 쓴다.
public final class BusinessDayCutoff {

    public static final int CUTOFF_HOUR = 4;

    private BusinessDayCutoff() {}

    // 주어진 시각이 속한 businessDate
    public static LocalDate of(LocalDateTime dateTime) {
        return dateTime.minusHours(CUTOFF_HOUR).toLocalDate();
    }

    // 주어진 시각이 속한 "영업월"의 1일 (1일 04:00 이전이면 지난달 1일)
    public static LocalDate firstDayOfBusinessMonth(LocalDateTime dateTime) {
        return of(dateTime).withDayOfMonth(1);
    }

    // 주어진 businessDate 가 시작되는 실제 시각 (그 날 04:00)
    public static LocalDateTime startTimeOfBusinessDate(LocalDate date) {
        return date.atTime(CUTOFF_HOUR, 0);
    }
}
