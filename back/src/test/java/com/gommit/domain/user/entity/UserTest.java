package com.gommit.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("User — 전역 인증 스트릭(대상일 기준)")
class UserTest {

    private static final LocalDate D10 = LocalDate.of(2026, 9, 10);

    private User user() {
        return new User("u@example.com", "{bcrypt}x", "유저");
    }

    private void setStreak(User user, int personalStreak, int bestStreak, LocalDate lastCheckedInDate) {
        ReflectionTestUtils.setField(user, "personalStreak", personalStreak);
        ReflectionTestUtils.setField(user, "bestStreak", bestStreak);
        ReflectionTestUtils.setField(user, "lastCheckedInDate", lastCheckedInDate);
    }

    @Test
    @DisplayName("첫 완료면 스트릭 1")
    void first() {
        User user = user();
        user.applyDailyCompletion(D10, D10.minusDays(1));
        assertThat(user.getPersonalStreak()).isEqualTo(1);
        assertThat(user.getBestStreak()).isEqualTo(1);
    }

    @Test
    @DisplayName("마지막 완료일이 직전 대상일과 같으면 +1")
    void consecutiveExact() {
        User user = user();
        setStreak(user, 3, 5, D10.minusDays(1));
        user.applyDailyCompletion(D10, D10.minusDays(1));
        assertThat(user.getPersonalStreak()).isEqualTo(4);
    }

    @Test
    @DisplayName("마지막 완료일이 직전 대상일보다 뒤면(그 사이 다른 챌린지로 활동) +1")
    void consecutiveAfterPreviousCheckInDay() {
        User user = user();
        // 트리거 챌린지의 직전 대상일은 9/7 인데, 유저는 9/9 에 다른 챌린지로 활동했다.
        setStreak(user, 3, 5, D10.minusDays(1));
        user.applyDailyCompletion(D10, D10.minusDays(3));
        assertThat(user.getPersonalStreak()).isEqualTo(4);
    }

    @Test
    @DisplayName("마지막 완료일이 직전 대상일보다 앞이면 1로 리셋")
    void resetWhenGapBeforePreviousCheckInDay() {
        User user = user();
        setStreak(user, 8, 8, D10.minusDays(5));
        user.applyDailyCompletion(D10, D10.minusDays(2));
        assertThat(user.getPersonalStreak()).isEqualTo(1);
        assertThat(user.getBestStreak()).isEqualTo(8);
    }

    @Test
    @DisplayName("같은 날 다시 호출되면 변화 없음")
    void idempotentPerDay() {
        User user = user();
        setStreak(user, 4, 4, D10);
        user.applyDailyCompletion(D10, D10.minusDays(1));
        assertThat(user.getPersonalStreak()).isEqualTo(4);
    }
}
