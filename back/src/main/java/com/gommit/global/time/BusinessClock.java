package com.gommit.global.time;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 주입된 Clock(app.time-zone, 기본 Asia/Seoul) 기준 "현재 businessDate".
// 04:00 컷오프 변환은 BusinessDayCutoff 에 위임한다.
@Component
@RequiredArgsConstructor
public class BusinessClock {

    private final Clock clock;

    public LocalDate today() {
        return BusinessDayCutoff.of(LocalDateTime.now(clock));
    }

    public LocalDate firstDayOfBusinessMonth() {
        return BusinessDayCutoff.firstDayOfBusinessMonth(LocalDateTime.now(clock));
    }
}
