package com.gommit.domain.challenge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeMember;
import com.gommit.domain.challenge.entity.ChallengeMemberRole;
import com.gommit.domain.challenge.entity.ChallengeMemberStatus;
import com.gommit.domain.challenge.entity.FrequencyType;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.challenge.repository.ChallengeRepository;
import com.gommit.domain.point.entity.GroupPointReason;
import com.gommit.domain.point.service.GroupPointService;
import com.gommit.domain.user.service.UserService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChallengeStreakService — 하루 완료 시 스트릭 갱신")
class ChallengeStreakServiceTest {

    private static final Long CHALLENGE_ID = 10L;
    private static final LocalDate START = LocalDate.of(2026, 9, 1);
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 10);

    @Mock
    private ChallengeRepository challengeRepository;

    @Mock
    private ChallengeMemberRepository challengeMemberRepository;

    @Mock
    private UserService userService;

    @Mock
    private GroupPointService groupPointService;

    private ChallengeStreakService service;

    @BeforeEach
    void setUp() {
        service = new ChallengeStreakService(
                challengeRepository,
                challengeMemberRepository,
                new ChallengeProgressCalculator(),
                userService,
                groupPointService);
    }

    private Challenge dailyChallenge() {
        Challenge challenge = Challenge.builder()
                .groupId(1L)
                .seqNo(1)
                .startDate(START)
                .endDate(START.plusDays(30))
                .frequencyType(FrequencyType.DAILY)
                .frequencyValue(null)
                .daysOfWeek(null)
                .dailyCheckInCount(1)
                .requiredDayCount(31)
                .groupCurrentStreak(0)
                .groupBestStreak(0)
                .allowPhoto(true)
                .build();
        challenge.activate();
        return challenge;
    }

    private ChallengeMember member(Long userId, int currentStreak, LocalDate lastCompletedDate) {
        ChallengeMember member = ChallengeMember.builder()
                .challenge(null)
                .userId(userId)
                .role(ChallengeMemberRole.MEMBER)
                .build();
        ReflectionTestUtils.setField(member, "currentStreak", currentStreak);
        ReflectionTestUtils.setField(member, "lastCompletedDate", lastCompletedDate);
        return member;
    }

    @Nested
    @DisplayName("개인 스트릭")
    class MemberStreak {

        @Test
        @DisplayName("직전 대상일에도 완료했으면 +1")
        void consecutive() {
            Challenge challenge = dailyChallenge();
            ChallengeMember me = member(1L, 4, TODAY.minusDays(1));
            when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(challenge));
            when(challengeMemberRepository.findByChallengeIdAndUserId(CHALLENGE_ID, 1L))
                    .thenReturn(Optional.of(me));
            when(challengeMemberRepository.findAllByChallengeIdAndStatus(CHALLENGE_ID, ChallengeMemberStatus.ACTIVE))
                    .thenReturn(List.of(me));

            MemberCheckInResult result = service.onMemberDailyComplete(CHALLENGE_ID, 1L, TODAY);

            assertThat(result.memberCurrentStreak()).isEqualTo(5);
            assertThat(me.getBestStreak()).isEqualTo(5);
            // 유저 전역 스트릭도 같은 tx 에서 갱신 — 직전 대상일(DAILY 이므로 어제)을 넘긴다.
            verify(userService).recordDailyCompletion(1L, TODAY, TODAY.minusDays(1));
        }

        @Test
        @DisplayName("직전 대상일에 완료하지 않았으면 1로 리셋")
        void reset() {
            Challenge challenge = dailyChallenge();
            ChallengeMember me = member(1L, 4, TODAY.minusDays(3));
            when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(challenge));
            when(challengeMemberRepository.findByChallengeIdAndUserId(CHALLENGE_ID, 1L))
                    .thenReturn(Optional.of(me));
            when(challengeMemberRepository.findAllByChallengeIdAndStatus(CHALLENGE_ID, ChallengeMemberStatus.ACTIVE))
                    .thenReturn(List.of(me));

            MemberCheckInResult result = service.onMemberDailyComplete(CHALLENGE_ID, 1L, TODAY);

            assertThat(result.memberCurrentStreak()).isEqualTo(1);
        }

        @Test
        @DisplayName("같은 날 두 번 호출돼도 스트릭은 한 번만 오른다")
        void idempotentPerDay() {
            Challenge challenge = dailyChallenge();
            ChallengeMember me = member(1L, 4, TODAY.minusDays(1));
            when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(challenge));
            when(challengeMemberRepository.findByChallengeIdAndUserId(CHALLENGE_ID, 1L))
                    .thenReturn(Optional.of(me));
            when(challengeMemberRepository.findAllByChallengeIdAndStatus(CHALLENGE_ID, ChallengeMemberStatus.ACTIVE))
                    .thenReturn(List.of(me));

            service.onMemberDailyComplete(CHALLENGE_ID, 1L, TODAY);
            MemberCheckInResult second = service.onMemberDailyComplete(CHALLENGE_ID, 1L, TODAY);

            assertThat(second.memberCurrentStreak()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("그룹 스트릭")
    class GroupStreak {

        @Test
        @DisplayName("이 인증으로 ACTIVE 전원이 완료되면 그룹 스트릭이 오르고 groupJustCompleted=true")
        void allComplete() {
            Challenge challenge = dailyChallenge();
            ChallengeMember me = member(1L, 0, null);
            ChallengeMember other = member(2L, 0, TODAY); // 이미 오늘 완료
            when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(challenge));
            when(challengeMemberRepository.findByChallengeIdAndUserId(CHALLENGE_ID, 1L))
                    .thenReturn(Optional.of(me));
            when(challengeMemberRepository.findAllByChallengeIdAndStatus(CHALLENGE_ID, ChallengeMemberStatus.ACTIVE))
                    .thenReturn(List.of(me, other));

            MemberCheckInResult result = service.onMemberDailyComplete(CHALLENGE_ID, 1L, TODAY);

            assertThat(result.groupCompletedCount()).isEqualTo(2);
            assertThat(result.groupTotalCount()).isEqualTo(2);
            assertThat(result.groupJustCompleted()).isTrue();
            assertThat(challenge.getGroupCurrentStreak()).isEqualTo(1);
            verify(groupPointService)
                    .reward(challenge.getGroupId(), 5, GroupPointReason.DAILY_ALL_COMPLETE, "전원 하루 인증 완료");
        }

        @Test
        @DisplayName("이미 그 날 그룹 완료 처리된 뒤면 그룹 포인트를 다시 적립하지 않는다")
        void noRewardWhenAlreadyCompletedToday() {
            Challenge challenge = dailyChallenge();
            ReflectionTestUtils.setField(challenge, "groupLastCompletedDate", TODAY);
            ChallengeMember me = member(1L, 0, null);
            ChallengeMember other = member(2L, 0, TODAY);
            when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(challenge));
            when(challengeMemberRepository.findByChallengeIdAndUserId(CHALLENGE_ID, 1L))
                    .thenReturn(Optional.of(me));
            when(challengeMemberRepository.findAllByChallengeIdAndStatus(CHALLENGE_ID, ChallengeMemberStatus.ACTIVE))
                    .thenReturn(List.of(me, other));

            MemberCheckInResult result = service.onMemberDailyComplete(CHALLENGE_ID, 1L, TODAY);

            assertThat(result.groupJustCompleted()).isFalse();
            verify(groupPointService, never()).reward(anyLong(), anyInt(), any(), anyString());
        }

        @Test
        @DisplayName("아직 완료하지 않은 멤버가 있으면 그룹 스트릭은 그대로")
        void notAllComplete() {
            Challenge challenge = dailyChallenge();
            ChallengeMember me = member(1L, 0, null);
            ChallengeMember other = member(2L, 0, null);
            when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(challenge));
            when(challengeMemberRepository.findByChallengeIdAndUserId(CHALLENGE_ID, 1L))
                    .thenReturn(Optional.of(me));
            when(challengeMemberRepository.findAllByChallengeIdAndStatus(CHALLENGE_ID, ChallengeMemberStatus.ACTIVE))
                    .thenReturn(List.of(me, other));

            MemberCheckInResult result = service.onMemberDailyComplete(CHALLENGE_ID, 1L, TODAY);

            assertThat(result.groupCompletedCount()).isEqualTo(1);
            assertThat(result.groupJustCompleted()).isFalse();
            assertThat(challenge.getGroupCurrentStreak()).isZero();
            verify(groupPointService, never()).reward(anyLong(), anyInt(), any(), anyString());
        }
    }
}
