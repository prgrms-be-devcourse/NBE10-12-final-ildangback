package com.gommit.domain.challenge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
import com.gommit.domain.point.config.PointProperties;
import com.gommit.domain.point.entity.GroupPointReason;
import com.gommit.domain.point.service.GroupPointService;
import com.gommit.domain.user.service.UserService;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChallengeStreakService — 하루 완료 시 그룹/유저 갱신")
class ChallengeStreakServiceTest {

    private static final Long CHALLENGE_ID = 10L;
    private static final LocalDate START = LocalDate.of(2026, 9, 1);
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 10);

    @Mock
    private ChallengeRepository challengeRepository;

    @Mock
    private ChallengeMemberRepository challengeMemberRepository;

    @Mock
    private GroupDailyCompletionReader groupDailyCompletionReader;

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
                groupDailyCompletionReader,
                userService,
                groupPointService,
                new PointProperties(10, 5, 0, 0));
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

    private ChallengeMember member(Long userId) {
        return ChallengeMember.builder()
                .challenge(null)
                .userId(userId)
                .role(ChallengeMemberRole.MEMBER)
                .build();
    }

    private void stubActiveMembers(Long... userIds) {
        ChallengeMember[] members = Arrays.stream(userIds).map(this::member).toArray(ChallengeMember[]::new);
        when(challengeMemberRepository.findAllByChallengeIdAndStatus(CHALLENGE_ID, ChallengeMemberStatus.ACTIVE))
                .thenReturn(Arrays.asList(members));
    }

    // 새 트랜잭션 읽기 결과 — 호출자 자신은 아직 커밋 전이라 포함되지 않는다.
    private void stubOthersCompleted(Long... userIds) {
        Set<Long> completed = new HashSet<>(Arrays.asList(userIds));
        when(groupDailyCompletionReader.completedMemberIds(eq(CHALLENGE_ID), eq(TODAY), anyInt(), any()))
                .thenReturn(completed);
    }

    @Test
    @DisplayName("유저 전역 스트릭을 직전 대상일과 함께 넘긴다")
    void delegatesUserStreak() {
        Challenge challenge = dailyChallenge();
        when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(challenge));
        stubActiveMembers(1L);
        stubOthersCompleted();

        service.onMemberDailyComplete(CHALLENGE_ID, 1L, TODAY);

        verify(userService).recordDailyCompletion(1L, TODAY, TODAY.minusDays(1));
    }

    @Test
    @DisplayName("이 인증으로 ACTIVE 전원이 완료되면 그룹 스트릭이 오르고 groupJustCompleted=true")
    void allComplete() {
        Challenge challenge = dailyChallenge();
        when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(challenge));
        stubActiveMembers(1L, 2L);
        stubOthersCompleted(2L); // 2번은 이미 오늘 완료

        MemberCheckInResult result = service.onMemberDailyComplete(CHALLENGE_ID, 1L, TODAY);

        assertThat(result.groupCompletedCount()).isEqualTo(2);
        assertThat(result.groupTotalCount()).isEqualTo(2);
        assertThat(result.groupJustCompleted()).isTrue();
        assertThat(challenge.getGroupCurrentStreak()).isEqualTo(1);
        verify(groupPointService).reward(challenge.getGroupId(), 5, GroupPointReason.DAILY_ALL_COMPLETE, "전원 하루 인증 완료");
    }

    @Test
    @DisplayName("이미 그 날 그룹 완료 처리된 뒤면 그룹 포인트를 다시 적립하지 않는다")
    void noRewardWhenAlreadyCompletedToday() {
        Challenge challenge = dailyChallenge();
        ReflectionTestUtils.setField(challenge, "groupLastCompletedDate", TODAY);
        when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(challenge));
        stubActiveMembers(1L, 2L);
        stubOthersCompleted(2L);

        MemberCheckInResult result = service.onMemberDailyComplete(CHALLENGE_ID, 1L, TODAY);

        assertThat(result.groupJustCompleted()).isFalse();
        verify(groupPointService, never()).reward(anyLong(), anyInt(), any(), anyString());
    }

    @Test
    @DisplayName("그룹 포인트 적립이 실패하면 예외를 전파한다 (그룹 스트릭 갱신도 같은 tx 라 롤백됨)")
    void propagatesGroupRewardFailure() {
        Challenge challenge = dailyChallenge();
        when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(challenge));
        stubActiveMembers(1L, 2L);
        stubOthersCompleted(2L);
        doThrow(new IllegalStateException("group_points 락 타임아웃"))
                .when(groupPointService)
                .reward(anyLong(), anyInt(), any(), anyString());

        assertThatThrownBy(() -> service.onMemberDailyComplete(CHALLENGE_ID, 1L, TODAY))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("아직 완료하지 않은 멤버가 있으면 그룹 스트릭은 그대로")
    void notAllComplete() {
        Challenge challenge = dailyChallenge();
        when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(challenge));
        stubActiveMembers(1L, 2L);
        stubOthersCompleted(); // 2번은 아직

        MemberCheckInResult result = service.onMemberDailyComplete(CHALLENGE_ID, 1L, TODAY);

        assertThat(result.groupCompletedCount()).isEqualTo(1);
        assertThat(result.groupJustCompleted()).isFalse();
        assertThat(challenge.getGroupCurrentStreak()).isZero();
        verify(groupPointService, never()).reward(anyLong(), anyInt(), any(), anyString());
    }
}
