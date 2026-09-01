package com.gommit.domain.challenge.entity;

import java.time.DayOfWeek;

public enum Weekday {
    MON,
    TUE,
    WED,
    THU,
    FRI,
    SAT,
    SUN;

    public static Weekday getWeekday(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> MON;
            case TUESDAY -> TUE;
            case WEDNESDAY -> WED;
            case THURSDAY -> THU;
            case FRIDAY -> FRI;
            case SATURDAY -> SAT;
            case SUNDAY -> SUN;
        };
    }
}
