package com.gommit.domain.challenge.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ChallengeMember — 개인 스트릭")
class ChallengeMemberTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 10);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);

    private ChallengeMember member() {
        return ChallengeMember.builder()
                .challenge(null)
                .userId(1L)
                .role(ChallengeMemberRole.MEMBER)
                .build();
    }

    @Nested
    @DisplayName("completeDay")
    class CompleteDay {

        @Test
        @DisplayName("첫 완료면 1")
        void first() {
            ChallengeMember member = member();

            member.completeDay(TODAY, YESTERDAY);

            assertThat(member.getCurrentStreak()).isEqualTo(1);
            assertThat(member.getBestStreak()).isEqualTo(1);
            assertThat(member.getLastCompletedDate()).isEqualTo(TODAY);
        }

        @Test
        @DisplayName("직전 인증 대상일에도 채웠으면 +1, best 도 오른다")
        void consecutive() {
            ChallengeMember member = member();
            member.completeDay(YESTERDAY, YESTERDAY.minusDays(1));

            member.completeDay(TODAY, YESTERDAY);

            assertThat(member.getCurrentStreak()).isEqualTo(2);
            assertThat(member.getBestStreak()).isEqualTo(2);
        }

        @Test
        @DisplayName("직전 인증 대상일이 비면 1 로 리셋, best 는 유지")
        void gapResets() {
            ChallengeMember member = member();
            member.completeDay(TODAY.minusDays(4), TODAY.minusDays(5));
            member.completeDay(TODAY.minusDays(3), TODAY.minusDays(4)); // currentStreak=2, best=2

            member.completeDay(TODAY, YESTERDAY); // 직전 대상일(어제) 안 채움

            assertThat(member.getCurrentStreak()).isEqualTo(1);
            assertThat(member.getBestStreak()).isEqualTo(2);
        }

        @Test
        @DisplayName("같은 businessDate 로 재호출하면 무시한다")
        void idempotentPerDay() {
            ChallengeMember member = member();
            member.completeDay(YESTERDAY, YESTERDAY.minusDays(1));
            member.completeDay(TODAY, YESTERDAY);

            member.completeDay(TODAY, YESTERDAY);

            assertThat(member.getCurrentStreak()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("currentStreakAsOf — 조회 시점 보정")
    class CurrentStreakAsOf {

        @Test
        @DisplayName("완료 이력이 없으면 0")
        void neverCompleted() {
            assertThat(member().currentStreakAsOf(TODAY, YESTERDAY)).isZero();
        }

        @Test
        @DisplayName("오늘 이미 채웠으면 저장값 그대로")
        void completedToday() {
            ChallengeMember member = member();
            member.completeDay(YESTERDAY, YESTERDAY.minusDays(1));
            member.completeDay(TODAY, YESTERDAY);

            assertThat(member.currentStreakAsOf(TODAY, YESTERDAY)).isEqualTo(2);
        }

        @Test
        @DisplayName("직전 인증 대상일까지 완료가 이어졌으면(오늘은 아직) 저장값 그대로")
        void aliveUntilPreviousCheckInDay() {
            ChallengeMember member = member();
            member.completeDay(YESTERDAY, YESTERDAY.minusDays(1)); // currentStreak=1, lastCompleted=어제

            assertThat(member.currentStreakAsOf(TODAY, YESTERDAY)).isEqualTo(1);
        }

        @Test
        @DisplayName("직전 인증 대상일보다 이전이 마지막이면(중간에 빠졌으면) 0")
        void brokenBeforePreviousCheckInDay() {
            ChallengeMember member = member();
            member.completeDay(TODAY.minusDays(3), TODAY.minusDays(4));

            assertThat(member.currentStreakAsOf(TODAY, YESTERDAY)).isZero();
        }
    }
}
