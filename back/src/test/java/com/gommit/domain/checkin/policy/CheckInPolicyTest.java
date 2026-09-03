package com.gommit.domain.checkin.policy;

import static com.gommit.domain.checkin.CheckInFixture.END;
import static com.gommit.domain.checkin.CheckInFixture.START;
import static com.gommit.domain.checkin.CheckInFixture.challenge;
import static com.gommit.domain.checkin.CheckInFixture.dailyChallenge;
import static org.assertj.core.api.Assertions.assertThat;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.FrequencyType;
import com.gommit.domain.checkin.entity.CheckInType;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("CheckInPolicy — 인증 대상일/허용 방식 판정")
class CheckInPolicyTest {

    private final CheckInPolicy policy = new CheckInPolicy();

    @Nested
    @DisplayName("isCheckInDay — 기간 경계")
    class Range {

        @Test
        @DisplayName("시작일 이전은 대상일이 아니다")
        void beforeStart() {
            assertThat(policy.isCheckInDay(dailyChallenge(1L, 1), START.minusDays(1)))
                    .isFalse();
        }

        @Test
        @DisplayName("종료일 이후는 대상일이 아니다")
        void afterEnd() {
            assertThat(policy.isCheckInDay(dailyChallenge(1L, 1), END.plusDays(1)))
                    .isFalse();
        }

        @Test
        @DisplayName("시작일 당일은 대상일이다")
        void startInclusive() {
            assertThat(policy.isCheckInDay(dailyChallenge(1L, 1), START)).isTrue();
        }
    }

    @Nested
    @DisplayName("isCheckInDay — DAILY")
    class Daily {

        @Test
        @DisplayName("기간 내 모든 날이 대상일")
        void everyDay() {
            Challenge challenge = dailyChallenge(1L, 1);
            assertThat(policy.isCheckInDay(challenge, START.plusDays(10))).isTrue();
            assertThat(policy.isCheckInDay(challenge, START.plusDays(11))).isTrue();
        }
    }

    @Nested
    @DisplayName("isCheckInDay — DAYS_OF_WEEK")
    class DaysOfWeek {

        @Test
        @DisplayName("지정 요일만 대상일 (약어 MON/TUE...)")
        void abbreviation() {
            LocalDate day = START.plusDays(14);
            String token = day.getDayOfWeek().name().substring(0, 3); // 예: "TUE"
            Challenge challenge = challenge(1L, FrequencyType.DAYS_OF_WEEK, null, token, 1, true);

            assertThat(policy.isCheckInDay(challenge, day)).isTrue();
            assertThat(policy.isCheckInDay(challenge, day.plusDays(1))).isFalse();
        }

        @Test
        @DisplayName("풀네임(MONDAY 등)도 인식한다")
        void fullName() {
            LocalDate day = START.plusDays(14);
            String token = day.getDayOfWeek().name(); // 예: "TUESDAY"
            Challenge challenge = challenge(1L, FrequencyType.DAYS_OF_WEEK, null, token, 1, true);

            assertThat(policy.isCheckInDay(challenge, day)).isTrue();
        }

        @Test
        @DisplayName("여러 요일 CSV 중 하나만 맞아도 대상일")
        void csv() {
            LocalDate day = START.plusDays(14);
            String token = day.getDayOfWeek().name().substring(0, 3);
            Challenge challenge = challenge(1L, FrequencyType.DAYS_OF_WEEK, null, "SUN," + token + ",WED", 1, true);

            assertThat(policy.isCheckInDay(challenge, day)).isTrue();
        }
    }

    @Nested
    @DisplayName("isCheckInDay — EVERY_N_DAYS")
    class EveryNDays {

        @Test
        @DisplayName("시작일로부터 N일 간격만 대상일 (N=3)")
        void everyThirdDay() {
            Challenge challenge = challenge(1L, FrequencyType.EVERY_N_DAYS, 3, null, 1, true);

            assertThat(policy.isCheckInDay(challenge, START)).isTrue(); // 0일차
            assertThat(policy.isCheckInDay(challenge, START.plusDays(1))).isFalse();
            assertThat(policy.isCheckInDay(challenge, START.plusDays(2))).isFalse();
            assertThat(policy.isCheckInDay(challenge, START.plusDays(3))).isTrue();
            assertThat(policy.isCheckInDay(challenge, START.plusDays(6))).isTrue();
        }
    }

    @Nested
    @DisplayName("allowedTypes / allows")
    class Allowed {

        @Test
        @DisplayName("allowPhoto 면 PHOTO 만 허용")
        void photoAllowed() {
            Challenge challenge = dailyChallenge(1L, 1);
            assertThat(policy.allowedTypes(challenge)).containsExactly(CheckInType.PHOTO);
            assertThat(policy.allows(challenge, CheckInType.PHOTO)).isTrue();
        }

        @Test
        @DisplayName("allowPhoto 가 false 면 아무 방식도 허용하지 않는다")
        void nothingAllowed() {
            Challenge challenge = challenge(1L, FrequencyType.DAILY, null, null, 1, false);
            assertThat(policy.allowedTypes(challenge)).isEqualTo(List.of());
            assertThat(policy.allows(challenge, CheckInType.PHOTO)).isFalse();
        }
    }
}
