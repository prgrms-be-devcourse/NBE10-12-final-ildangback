package com.gommit.global.time;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 주입 가능한 "현재 시각" seam. 배포 환경 TZ 와 무관하게 businessDate 를 계산한다.
// 실제 04:00 컷오프 변환은 BusinessDateUtil 에 위임한다.
// 테스트에서는 Clock.fixed(...) 를 주입해 시각을 고정한다.
@Component
@RequiredArgsConstructor
public class BusinessClock {

    private final Clock clock;

    public LocalDate today() {
        return BusinessDateUtil.of(LocalDateTime.now(clock));
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
