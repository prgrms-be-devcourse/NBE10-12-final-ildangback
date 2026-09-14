package com.gommit.domain.group.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeMember;
import com.gommit.domain.challenge.entity.ChallengeMemberRole;
import com.gommit.domain.challenge.entity.ChallengeMemberStatus;
import com.gommit.domain.challenge.entity.ChallengeStatus;
import com.gommit.domain.challenge.entity.FrequencyType;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.challenge.repository.ChallengeRepository;
import com.gommit.domain.group.entity.ChallengeGroup;
import com.gommit.domain.group.entity.GroupCategory;
import com.gommit.domain.group.entity.GroupMember;
import com.gommit.domain.group.entity.GroupMemberStatus;
import com.gommit.domain.group.entity.KickVoteChoice;
import com.gommit.domain.group.entity.MapType;
import com.gommit.domain.group.entity.Visibility;
import com.gommit.domain.group.repository.ChallengeGroupRepository;
import com.gommit.domain.group.repository.GroupMemberRepository;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("OwnerKickVoteService 단위 테스트")
class OwnerKickVoteServiceTest {

    @Mock
    private ChallengeGroupRepository challengeGroupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private ChallengeRepository challengeRepository;

    @Mock
    private ChallengeMemberRepository challengeMemberRepository;

    @InjectMocks
    private OwnerKickVoteService ownerKickVoteService;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(ownerKickVoteService, "kickVoteExpiryHours", 24);
    }

    // ── 엔티티 픽스처 ──────────────────────────────────────────────────────────

    private ChallengeGroup group(Long id, Long ownerId) {
        ChallengeGroup g = ChallengeGroup.builder()
                .name("테스트 그룹")
                .description("설명")
                .category(GroupCategory.EXERCISE)
                .mapType(MapType.GYM)
                .visibility(Visibility.PUBLIC)
                .maxMembers(10)
                .ownerId(ownerId)
                .build();
        ReflectionTestUtils.setField(g, "id", id);
        ReflectionTestUtils.setField(g, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(g, "updatedAt", LocalDateTime.now());
        return g;
    }

    private GroupMember member(Long id, ChallengeGroup g, Long userId) {
        GroupMember m = GroupMember.builder().group(g).userId(userId).build();
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    private Challenge challenge(Long id, Long groupId) {
        Challenge c = Challenge.builder()
                .groupId(groupId)
                .seqNo(1)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(6))
                .frequencyType(FrequencyType.DAILY)
                .frequencyValue(null)
                .daysOfWeek(null)
                .dailyCheckInCount(1)
                .requiredDayCount(7)
                .groupCurrentStreak(0)
                .groupBestStreak(0)
                .allowPhoto(true)
                .build();
        c.activate();
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    private Challenge readyChallenge(Long id, Long groupId) {
        Challenge c = Challenge.builder()
                .groupId(groupId)
                .seqNo(2)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(7))
                .frequencyType(FrequencyType.DAILY)
                .frequencyValue(null)
                .daysOfWeek(null)
                .dailyCheckInCount(1)
                .requiredDayCount(7)
                .groupCurrentStreak(0)
                .groupBestStreak(0)
                .allowPhoto(true)
                .build();
        // activate() 미호출 → READY 상태 유지
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    private ChallengeMember challengeMember(Long id, Challenge c, Long userId, ChallengeMemberRole role) {
        ChallengeMember m =
                ChallengeMember.builder().challenge(c).userId(userId).role(role).build();
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    private void assertBusinessException(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    /**
     * checkAndConclude / buildResponse 에서 사용하는 카운트 쿼리를 공통으로 스텁한다.
     * agree/disagree/total 을 원하는 값으로 지정해 과반수 판정 시나리오를 제어한다.
     */
    private void stubCounts(Long groupId, long total, long agree, long disagree) {
        when(groupMemberRepository.countByGroupIdAndStatus(groupId, GroupMemberStatus.ACTIVE))
                .thenReturn(total);
        when(groupMemberRepository.countByGroupIdAndStatusAndKickVoteChoice(
                        groupId, GroupMemberStatus.ACTIVE, KickVoteChoice.AGREE))
                .thenReturn(agree);
        when(groupMemberRepository.countByGroupIdAndStatusAndKickVoteChoice(
                        groupId, GroupMemberStatus.ACTIVE, KickVoteChoice.DISAGREE))
                .thenReturn(disagree);
    }

    // ── 투표 개시 ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("initiateVote")
    class InitiateVote {

        @Test
        @DisplayName("정상 개시: 그룹 찾기 → 멤버 확인 → 챌린지 확인 → startKickVote → auto AGREE → 응답")
        void happyPath() {
            // given
            ChallengeGroup group = group(12L, 1L);
            GroupMember initiator = member(30L, group, 2L);
            Challenge activeChallenge = challenge(50L, 12L);
            // 3명 총(n=2), agree=1 → 2 > 2 → FALSE (투표 지속)
            stubCounts(12L, 3, 1, 0);
            when(challengeGroupRepository.findByIdWithLock(12L)).thenReturn(Optional.of(group));
            when(groupMemberRepository.findByGroupIdAndUserId(12L, 2L)).thenReturn(Optional.of(initiator));
            when(challengeRepository.findFirstByGroupIdAndStatus(12L, ChallengeStatus.ACTIVE))
                    .thenReturn(Optional.of(activeChallenge));
            when(groupMemberRepository.findByGroupIdAndUserId(12L, 2L)).thenReturn(Optional.of(initiator));

            // when
            var response = ownerKickVoteService.initiateVote(12L, 2L);

            // then
            assertThat(response.inProgress()).isTrue();
            assertThat(response.agreeCount()).isEqualTo(1);
            assertThat(response.disagreeCount()).isEqualTo(0);
            assertThat(response.myChoice()).isEqualTo(KickVoteChoice.AGREE);
            assertThat(group.hasActiveKickVote()).isTrue();
            assertThat(initiator.getKickVoteChoice()).isEqualTo(KickVoteChoice.AGREE);
        }

        @Test
        @DisplayName("그룹이 없으면 GROUP_NOT_FOUND")
        void groupNotFound() {
            when(challengeGroupRepository.findByIdWithLock(999L)).thenReturn(Optional.empty());
            assertBusinessException(() -> ownerKickVoteService.initiateVote(999L, 2L), ErrorCode.GROUP_NOT_FOUND);
        }

        @Test
        @DisplayName("그룹 멤버가 아니면 NOT_GROUP_MEMBER")
        void notGroupMember() {
            ChallengeGroup group = group(12L, 1L);
            when(challengeGroupRepository.findByIdWithLock(12L)).thenReturn(Optional.of(group));
            when(groupMemberRepository.findByGroupIdAndUserId(12L, 99L)).thenReturn(Optional.empty());
            assertBusinessException(() -> ownerKickVoteService.initiateVote(12L, 99L), ErrorCode.NOT_GROUP_MEMBER);
        }

        @Test
        @DisplayName("KICKED/LEFT 멤버면 NOT_GROUP_MEMBER")
        void inactiveMemberCannotInitiate() {
            ChallengeGroup group = group(12L, 1L);
            GroupMember kicked = member(30L, group, 2L);
            kicked.kick();
            when(challengeGroupRepository.findByIdWithLock(12L)).thenReturn(Optional.of(group));
            when(groupMemberRepository.findByGroupIdAndUserId(12L, 2L)).thenReturn(Optional.of(kicked));
            assertBusinessException(() -> ownerKickVoteService.initiateVote(12L, 2L), ErrorCode.NOT_GROUP_MEMBER);
        }

        @Test
        @DisplayName("방장이 개시하면 KICK_VOTE_OWNER_CANNOT_INITIATE")
        void ownerCannotInitiate() {
            ChallengeGroup group = group(12L, 1L);
            GroupMember ownerMember = member(30L, group, 1L);
            when(challengeGroupRepository.findByIdWithLock(12L)).thenReturn(Optional.of(group));
            when(groupMemberRepository.findByGroupIdAndUserId(12L, 1L)).thenReturn(Optional.of(ownerMember));
            assertBusinessException(
                    () -> ownerKickVoteService.initiateVote(12L, 1L), ErrorCode.KICK_VOTE_OWNER_CANNOT_INITIATE);
        }

        @Test
        @DisplayName("ACTIVE 챌린지 없으면 GROUP_MEMBER_KICK_NOT_ALLOWED")
        void noActiveChallenge() {
            ChallengeGroup group = group(12L, 1L);
            GroupMember initiator = member(30L, group, 2L);
            when(challengeGroupRepository.findByIdWithLock(12L)).thenReturn(Optional.of(group));
            when(groupMemberRepository.findByGroupIdAndUserId(12L, 2L)).thenReturn(Optional.of(initiator));
            when(challengeRepository.findFirstByGroupIdAndStatus(12L, ChallengeStatus.ACTIVE))
                    .thenReturn(Optional.empty());
            assertBusinessException(
                    () -> ownerKickVoteService.initiateVote(12L, 2L), ErrorCode.GROUP_MEMBER_KICK_NOT_ALLOWED);
        }

        @Test
        @DisplayName("이미 투표 진행 중이면 KICK_VOTE_ALREADY_IN_PROGRESS")
        void alreadyInProgress() {
            ChallengeGroup group = group(12L, 1L);
            group.startKickVote(); // 이미 진행 중
            GroupMember initiator = member(30L, group, 2L);
            when(challengeGroupRepository.findByIdWithLock(12L)).thenReturn(Optional.of(group));
            when(groupMemberRepository.findByGroupIdAndUserId(12L, 2L)).thenReturn(Optional.of(initiator));
            when(challengeRepository.findFirstByGroupIdAndStatus(12L, ChallengeStatus.ACTIVE))
                    .thenReturn(Optional.of(challenge(50L, 12L)));
            assertBusinessException(
                    () -> ownerKickVoteService.initiateVote(12L, 2L), ErrorCode.KICK_VOTE_ALREADY_IN_PROGRESS);
        }

        @Test
        @DisplayName("개시 즉시 과반수 달성(2인 그룹)이면 방장이 강퇴된다")
        void immediatelyPassesInTwoMemberGroup() {
            // 2명(n=1), agree=1 → 1*2=2 > 1 → TRUE → 가결
            ChallengeGroup group = group(12L, 1L);
            GroupMember initiator = member(30L, group, 2L);
            GroupMember ownerMember = member(31L, group, 1L);
            Challenge activeChallenge = challenge(50L, 12L);
            ChallengeMember ownerChallengeMember = challengeMember(70L, activeChallenge, 1L, ChallengeMemberRole.OWNER);
            stubCounts(12L, 2, 1, 0);
            when(challengeGroupRepository.findByIdWithLock(12L)).thenReturn(Optional.of(group));
            when(groupMemberRepository.findByGroupIdAndUserId(12L, 2L)).thenReturn(Optional.of(initiator));
            when(challengeRepository.findFirstByGroupIdAndStatus(12L, ChallengeStatus.ACTIVE))
                    .thenReturn(Optional.of(activeChallenge));
            // executeOwnerKick 스텁
            when(groupMemberRepository.findByGroupIdAndUserId(12L, 1L)).thenReturn(Optional.of(ownerMember));
            when(challengeMemberRepository.findByChallengeIdAndUserId(50L, 1L))
                    .thenReturn(Optional.of(ownerChallengeMember));
            when(groupMemberRepository.findAllByGroupIdAndStatus(12L, GroupMemberStatus.ACTIVE))
                    .thenReturn(List.of(initiator));

            // when
            var response = ownerKickVoteService.initiateVote(12L, 2L);

            // then
            assertThat(response.inProgress()).isFalse();
            assertThat(ownerMember.getStatus()).isEqualTo(GroupMemberStatus.KICKED);
            assertThat(ownerChallengeMember.getStatus()).isEqualTo(ChallengeMemberStatus.KICKED);
            assertThat(ownerChallengeMember.getRole()).isEqualTo(ChallengeMemberRole.MEMBER); // OWNER→MEMBER→KICKED
            verify(groupMemberRepository).resetAllKickVoteChoices(12L, KickVoteChoice.NONE);
        }
    }

    // ── 투표 참여 ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("castVote")
    class CastVote {

        @Test
        @DisplayName("그룹이 없으면 GROUP_NOT_FOUND")
        void groupNotFound() {
            when(challengeGroupRepository.findByIdWithLock(999L)).thenReturn(Optional.empty());
            assertBusinessException(
                    () -> ownerKickVoteService.castVote(999L, 2L, KickVoteChoice.AGREE), ErrorCode.GROUP_NOT_FOUND);
        }

        @Test
        @DisplayName("방장이 투표하면 KICK_VOTE_OWNER_CANNOT_VOTE")
        void ownerCannotVote() {
            ChallengeGroup group = group(12L, 1L);
            GroupMember ownerMember = member(30L, group, 1L);
            when(challengeGroupRepository.findByIdWithLock(12L)).thenReturn(Optional.of(group));
            when(groupMemberRepository.findByGroupIdAndUserId(12L, 1L)).thenReturn(Optional.of(ownerMember));
            assertBusinessException(
                    () -> ownerKickVoteService.castVote(12L, 1L, KickVoteChoice.AGREE),
                    ErrorCode.KICK_VOTE_OWNER_CANNOT_VOTE);
        }

        @Test
        @DisplayName("진행 중인 투표가 없으면 RESOURCE_NOT_FOUND")
        void noActiveVote() {
            ChallengeGroup group = group(12L, 1L); // kickVoteStartedAt = null
            GroupMember voter = member(30L, group, 2L);
            when(challengeGroupRepository.findByIdWithLock(12L)).thenReturn(Optional.of(group));
            when(groupMemberRepository.findByGroupIdAndUserId(12L, 2L)).thenReturn(Optional.of(voter));
            assertBusinessException(
                    () -> ownerKickVoteService.castVote(12L, 2L, KickVoteChoice.AGREE), ErrorCode.RESOURCE_NOT_FOUND);
        }

        @Test
        @DisplayName("투표 기간이 만료됐으면 투표를 초기화하고 KICK_VOTE_EXPIRED")
        void expiredVote() {
            ChallengeGroup group = group(12L, 1L);
            group.startKickVote();
            // 25시간 전으로 되감기
            ReflectionTestUtils.setField(
                    group, "kickVoteStartedAt", LocalDateTime.now().minusHours(25));
            GroupMember voter = member(30L, group, 2L);
            when(challengeGroupRepository.findByIdWithLock(12L)).thenReturn(Optional.of(group));
            when(groupMemberRepository.findByGroupIdAndUserId(12L, 2L)).thenReturn(Optional.of(voter));

            assertBusinessException(
                    () -> ownerKickVoteService.castVote(12L, 2L, KickVoteChoice.AGREE), ErrorCode.KICK_VOTE_EXPIRED);
            assertThat(group.hasActiveKickVote()).isFalse();
            verify(groupMemberRepository).resetAllKickVoteChoices(12L, KickVoteChoice.NONE);
        }

        @Test
        @DisplayName("이미 투표했으면 KICK_VOTE_ALREADY_VOTED")
        void alreadyVoted() {
            ChallengeGroup group = group(12L, 1L);
            group.startKickVote();
            GroupMember voter = member(30L, group, 2L);
            voter.castKickVote(KickVoteChoice.AGREE); // 이미 투표
            when(challengeGroupRepository.findByIdWithLock(12L)).thenReturn(Optional.of(group));
            when(groupMemberRepository.findByGroupIdAndUserId(12L, 2L)).thenReturn(Optional.of(voter));
            assertBusinessException(
                    () -> ownerKickVoteService.castVote(12L, 2L, KickVoteChoice.DISAGREE),
                    ErrorCode.KICK_VOTE_ALREADY_VOTED);
        }

        @Test
        @DisplayName("AGREE 투표 → 카운트 반영 응답 반환, 투표 지속")
        void agreeVoteContinues() {
            ChallengeGroup group = group(12L, 1L);
            group.startKickVote();
            GroupMember voter = member(30L, group, 2L);
            // 5명(n=4), agree=2 → 4 > 4 → FALSE (투표 지속)
            stubCounts(12L, 5, 2, 0);
            when(challengeGroupRepository.findByIdWithLock(12L)).thenReturn(Optional.of(group));
            when(groupMemberRepository.findByGroupIdAndUserId(12L, 2L)).thenReturn(Optional.of(voter));

            var response = ownerKickVoteService.castVote(12L, 2L, KickVoteChoice.AGREE);

            assertThat(response.inProgress()).isTrue();
            assertThat(response.agreeCount()).isEqualTo(2);
            assertThat(voter.getKickVoteChoice()).isEqualTo(KickVoteChoice.AGREE);
        }

        @Test
        @DisplayName("DISAGREE 투표 → 카운트 반영 응답 반환, 투표 지속")
        void disagreeVoteContinues() {
            ChallengeGroup group = group(12L, 1L);
            group.startKickVote();
            GroupMember voter = member(30L, group, 2L);
            // 5명(n=4), disagree=1 → 2 >= 4 → FALSE (투표 지속)
            stubCounts(12L, 5, 1, 1);
            when(challengeGroupRepository.findByIdWithLock(12L)).thenReturn(Optional.of(group));
            when(groupMemberRepository.findByGroupIdAndUserId(12L, 2L)).thenReturn(Optional.of(voter));

            var response = ownerKickVoteService.castVote(12L, 2L, KickVoteChoice.DISAGREE);

            assertThat(response.inProgress()).isTrue();
            assertThat(response.disagreeCount()).isEqualTo(1);
            assertThat(voter.getKickVoteChoice()).isEqualTo(KickVoteChoice.DISAGREE);
        }

        @Test
        @DisplayName("과반수 찬성 달성 → 방장 KICKED, 새 방장 선정, 투표 초기화")
        void agreedMajorityKicksOwner() {
            // 3명(n=2), agree=2 → 4 > 2 → TRUE(가결)
            ChallengeGroup group = group(12L, 1L);
            group.startKickVote();
            GroupMember voter = member(30L, group, 2L);
            GroupMember ownerMember = member(31L, group, 1L);
            GroupMember remainingMember = member(32L, group, 3L);
            Challenge activeChallenge = challenge(50L, 12L);
            ChallengeMember ownerChallengeMember = challengeMember(70L, activeChallenge, 1L, ChallengeMemberRole.OWNER);
            ChallengeMember remainingChallengeMember =
                    challengeMember(71L, activeChallenge, 3L, ChallengeMemberRole.MEMBER);
            stubCounts(12L, 3, 2, 0);
            when(challengeGroupRepository.findByIdWithLock(12L)).thenReturn(Optional.of(group));
            when(groupMemberRepository.findByGroupIdAndUserId(12L, 2L)).thenReturn(Optional.of(voter));
            // executeOwnerKick
            when(groupMemberRepository.findByGroupIdAndUserId(12L, 1L)).thenReturn(Optional.of(ownerMember));
            when(challengeRepository.findFirstByGroupIdAndStatus(12L, ChallengeStatus.ACTIVE))
                    .thenReturn(Optional.of(activeChallenge));
            when(challengeMemberRepository.findByChallengeIdAndUserId(50L, 1L))
                    .thenReturn(Optional.of(ownerChallengeMember));
            when(groupMemberRepository.findAllByGroupIdAndStatus(12L, GroupMemberStatus.ACTIVE))
                    .thenReturn(List.of(remainingMember));
            when(challengeMemberRepository.findByChallengeIdAndUserId(50L, 3L))
                    .thenReturn(Optional.of(remainingChallengeMember));

            var response = ownerKickVoteService.castVote(12L, 2L, KickVoteChoice.AGREE);

            assertThat(response.inProgress()).isFalse();
            assertThat(ownerMember.getStatus()).isEqualTo(GroupMemberStatus.KICKED);
            assertThat(ownerChallengeMember.getStatus()).isEqualTo(ChallengeMemberStatus.KICKED);
            assertThat(remainingChallengeMember.getRole()).isEqualTo(ChallengeMemberRole.OWNER);
            assertThat(group.getOwnerId()).isEqualTo(3L);
            assertThat(group.hasActiveKickVote()).isFalse();
            verify(groupMemberRepository).resetAllKickVoteChoices(12L, KickVoteChoice.NONE);
        }

        @Test
        @DisplayName("과반수 반대 달성 → 투표 종료, 방장 유지")
        void disagreedMajorityEndsVote() {
            // 3명(n=2), disagree=1 → 2 >= 2 → TRUE(부결)
            ChallengeGroup group = group(12L, 1L);
            group.startKickVote();
            GroupMember voter = member(30L, group, 2L);
            stubCounts(12L, 3, 1, 1);
            when(challengeGroupRepository.findByIdWithLock(12L)).thenReturn(Optional.of(group));
            when(groupMemberRepository.findByGroupIdAndUserId(12L, 2L)).thenReturn(Optional.of(voter));

            var response = ownerKickVoteService.castVote(12L, 2L, KickVoteChoice.DISAGREE);

            assertThat(response.inProgress()).isFalse();
            assertThat(group.getOwnerId()).isEqualTo(1L); // 방장 유지
            assertThat(group.hasActiveKickVote()).isFalse();
            verify(groupMemberRepository).resetAllKickVoteChoices(12L, KickVoteChoice.NONE);
        }

        @Test
        @DisplayName("남은 멤버가 없으면 그룹이 종료된다")
        void groupEndsWhenNoRemainingMembers() {
            // 2명(n=1), agree=1 → 2 > 1 → TRUE(가결) → 남은 멤버 없음
            ChallengeGroup group = group(12L, 1L);
            group.startKickVote();
            GroupMember voter = member(30L, group, 2L);
            GroupMember ownerMember = member(31L, group, 1L);
            Challenge activeChallenge = challenge(50L, 12L);
            ChallengeMember ownerChallengeMember = challengeMember(70L, activeChallenge, 1L, ChallengeMemberRole.OWNER);
            stubCounts(12L, 2, 1, 0);
            when(challengeGroupRepository.findByIdWithLock(12L)).thenReturn(Optional.of(group));
            when(groupMemberRepository.findByGroupIdAndUserId(12L, 2L)).thenReturn(Optional.of(voter));
            when(groupMemberRepository.findByGroupIdAndUserId(12L, 1L)).thenReturn(Optional.of(ownerMember));
            when(challengeRepository.findFirstByGroupIdAndStatus(12L, ChallengeStatus.ACTIVE))
                    .thenReturn(Optional.of(activeChallenge));
            when(challengeMemberRepository.findByChallengeIdAndUserId(50L, 1L))
                    .thenReturn(Optional.of(ownerChallengeMember));
            when(groupMemberRepository.findAllByGroupIdAndStatus(12L, GroupMemberStatus.ACTIVE))
                    .thenReturn(List.of()); // 남은 멤버 없음

            ownerKickVoteService.castVote(12L, 2L, KickVoteChoice.AGREE);

            // 새 방장 선정 없이 그룹이 종료됨
            verify(challengeMemberRepository, never()).findByChallengeIdAndUserId(eq(50L), eq(2L));
        }

        @Test
        @DisplayName("READY 챌린지가 있으면 방장 강퇴 및 새 방장 승격이 ACTIVE·READY 모두 적용된다")
        void readyChallengeIsAlsoKickedAndReassigned() {
            // 3명(n=2), agree=2 → 가결
            ChallengeGroup group = group(12L, 1L);
            group.startKickVote();
            GroupMember voter = member(30L, group, 2L);
            GroupMember ownerMember = member(31L, group, 1L);
            GroupMember remainingMember = member(32L, group, 3L);
            Challenge activeChallenge = challenge(50L, 12L);
            Challenge ready = readyChallenge(51L, 12L);
            ChallengeMember ownerActiveM = challengeMember(70L, activeChallenge, 1L, ChallengeMemberRole.OWNER);
            ChallengeMember ownerReadyM = challengeMember(71L, ready, 1L, ChallengeMemberRole.OWNER);
            ChallengeMember remainActiveM = challengeMember(72L, activeChallenge, 3L, ChallengeMemberRole.MEMBER);
            ChallengeMember remainReadyM = challengeMember(73L, ready, 3L, ChallengeMemberRole.MEMBER);
            stubCounts(12L, 3, 2, 0);
            when(challengeGroupRepository.findByIdWithLock(12L)).thenReturn(Optional.of(group));
            when(groupMemberRepository.findByGroupIdAndUserId(12L, 2L)).thenReturn(Optional.of(voter));
            when(groupMemberRepository.findByGroupIdAndUserId(12L, 1L)).thenReturn(Optional.of(ownerMember));
            when(challengeRepository.findFirstByGroupIdAndStatus(12L, ChallengeStatus.ACTIVE))
                    .thenReturn(Optional.of(activeChallenge));
            when(challengeRepository.findFirstByGroupIdAndStatus(12L, ChallengeStatus.READY))
                    .thenReturn(Optional.of(ready));
            when(challengeMemberRepository.findByChallengeIdAndUserId(50L, 1L)).thenReturn(Optional.of(ownerActiveM));
            when(challengeMemberRepository.findByChallengeIdAndUserId(51L, 1L)).thenReturn(Optional.of(ownerReadyM));
            when(groupMemberRepository.findAllByGroupIdAndStatus(12L, GroupMemberStatus.ACTIVE))
                    .thenReturn(List.of(remainingMember));
            when(challengeMemberRepository.findByChallengeIdAndUserId(50L, 3L)).thenReturn(Optional.of(remainActiveM));
            when(challengeMemberRepository.findByChallengeIdAndUserId(51L, 3L)).thenReturn(Optional.of(remainReadyM));

            ownerKickVoteService.castVote(12L, 2L, KickVoteChoice.AGREE);

            assertThat(ownerActiveM.getStatus()).isEqualTo(ChallengeMemberStatus.KICKED);
            assertThat(ownerReadyM.getStatus()).isEqualTo(ChallengeMemberStatus.KICKED);
            assertThat(remainActiveM.getRole()).isEqualTo(ChallengeMemberRole.OWNER);
            assertThat(remainReadyM.getRole()).isEqualTo(ChallengeMemberRole.OWNER);
            assertThat(group.getOwnerId()).isEqualTo(3L);
        }
    }

    // ── 현황 조회 ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getVoteStatus")
    class GetVoteStatus {

        @Test
        @DisplayName("그룹 멤버가 아니면 NOT_GROUP_MEMBER")
        void notGroupMember() {
            ChallengeGroup group = group(12L, 1L);
            when(challengeGroupRepository.findById(12L)).thenReturn(Optional.of(group));
            when(groupMemberRepository.findByGroupIdAndUserId(12L, 99L)).thenReturn(Optional.empty());
            assertBusinessException(() -> ownerKickVoteService.getVoteStatus(12L, 99L), ErrorCode.NOT_GROUP_MEMBER);
        }

        @Test
        @DisplayName("그룹이 없으면 GROUP_NOT_FOUND")
        void groupNotFound() {
            when(challengeGroupRepository.findById(12L)).thenReturn(Optional.empty());
            assertBusinessException(() -> ownerKickVoteService.getVoteStatus(12L, 2L), ErrorCode.GROUP_NOT_FOUND);
        }

        @Test
        @DisplayName("진행 중인 투표가 없으면 RESOURCE_NOT_FOUND")
        void noActiveVote() {
            ChallengeGroup group = group(12L, 1L); // kickVoteStartedAt = null
            GroupMember requester = member(30L, group, 2L);
            when(challengeGroupRepository.findById(12L)).thenReturn(Optional.of(group));
            when(groupMemberRepository.findByGroupIdAndUserId(12L, 2L)).thenReturn(Optional.of(requester));
            assertBusinessException(() -> ownerKickVoteService.getVoteStatus(12L, 2L), ErrorCode.RESOURCE_NOT_FOUND);
        }

        @Test
        @DisplayName("투표 기간이 만료됐으면 초기화하고 KICK_VOTE_EXPIRED")
        void expiredVote() {
            ChallengeGroup group = group(12L, 1L);
            group.startKickVote();
            ReflectionTestUtils.setField(
                    group, "kickVoteStartedAt", LocalDateTime.now().minusHours(25));
            GroupMember requester = member(30L, group, 2L);
            when(challengeGroupRepository.findById(12L)).thenReturn(Optional.of(group));
            when(groupMemberRepository.findByGroupIdAndUserId(12L, 2L)).thenReturn(Optional.of(requester));

            assertBusinessException(() -> ownerKickVoteService.getVoteStatus(12L, 2L), ErrorCode.KICK_VOTE_EXPIRED);
            assertThat(group.hasActiveKickVote()).isFalse();
            verify(groupMemberRepository).resetAllKickVoteChoices(12L, KickVoteChoice.NONE);
        }

        @Test
        @DisplayName("정상 조회: inProgress, 카운트, myChoice 등 정보를 올바르게 반환한다")
        void happyPath() {
            ChallengeGroup group = group(12L, 1L);
            group.startKickVote();
            GroupMember requester = member(30L, group, 2L);
            requester.castKickVote(KickVoteChoice.AGREE);
            stubCounts(12L, 4, 1, 0);
            when(challengeGroupRepository.findById(12L)).thenReturn(Optional.of(group));
            when(groupMemberRepository.findByGroupIdAndUserId(12L, 2L)).thenReturn(Optional.of(requester));

            var response = ownerKickVoteService.getVoteStatus(12L, 2L);

            assertThat(response.inProgress()).isTrue();
            assertThat(response.targetOwnerId()).isEqualTo(1L);
            assertThat(response.agreeCount()).isEqualTo(1);
            assertThat(response.disagreeCount()).isEqualTo(0);
            assertThat(response.eligibleVoters()).isEqualTo(3); // totalActive(4) - 1
            assertThat(response.myChoice()).isEqualTo(KickVoteChoice.AGREE);
            assertThat(response.startedAt()).isNotNull();
            assertThat(response.expiresAt()).isNotNull();
        }
    }

    // ── 스케줄러 ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("expireOutdatedVotes")
    class ExpireOutdatedVotes {

        @Test
        @DisplayName("만료된 그룹들의 투표 데이터를 초기화한다")
        void resetsExpiredGroups() {
            when(groupMemberRepository.bulkResetExpiredKickVoteChoice(any(LocalDateTime.class)))
                    .thenReturn(2);
            when(challengeGroupRepository.bulkExpiredKickVotes(any(LocalDateTime.class)))
                    .thenReturn(2);

            ownerKickVoteService.expireOutdatedVotes();

            InOrder inOrder = inOrder(groupMemberRepository, challengeGroupRepository);
            inOrder.verify(groupMemberRepository).bulkResetExpiredKickVoteChoice(any(LocalDateTime.class));
            inOrder.verify(challengeGroupRepository).bulkExpiredKickVotes(any(LocalDateTime.class));
        }

        @Test
        @DisplayName("만료된 그룹이 없으면 아무것도 하지 않는다")
        void doesNothingWhenNothingExpired() {
            when(groupMemberRepository.bulkResetExpiredKickVoteChoice(any(LocalDateTime.class)))
                    .thenReturn(0);
            when(challengeGroupRepository.bulkExpiredKickVotes(any(LocalDateTime.class)))
                    .thenReturn(0);

            assertThatCode(() -> ownerKickVoteService.expireOutdatedVotes()).doesNotThrowAnyException();
        }
    }
}
