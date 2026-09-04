package com.gommit.domain.challenge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeMember;
import com.gommit.domain.challenge.entity.ChallengeMemberRole;
import com.gommit.domain.challenge.entity.ChallengeStatus;
import com.gommit.domain.challenge.entity.FrequencyType;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.challenge.repository.ChallengeRepository;
import com.gommit.domain.group.entity.ChallengeGroup;
import com.gommit.domain.group.entity.GroupCategory;
import com.gommit.domain.group.entity.GroupStatus;
import com.gommit.domain.group.entity.MapType;
import com.gommit.domain.group.entity.Visibility;
import com.gommit.domain.group.repository.ChallengeGroupRepository;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChallengeLifecycleServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Mock
    private ChallengeRepository challengeRepository;

    @Mock
    private ChallengeMemberRepository challengeMemberRepository;

    @Mock
    private ChallengeGroupRepository challengeGroupRepository;

    @InjectMocks
    private ChallengeLifecycleService challengeLifecycleService;

    private Challenge challenge(Long id, int seqNo, ChallengeStatus status, LocalDate startDate, LocalDate endDate) {
        Challenge challenge = Challenge.builder()
                .groupId(12L)
                .seqNo(seqNo)
                .startDate(startDate)
                .endDate(endDate)
                .frequencyType(FrequencyType.DAILY)
                .frequencyValue(null)
                .daysOfWeek(null)
                .dailyCheckInCount(1)
                .requiredDayCount(7)
                .groupCurrentStreak(0)
                .groupBestStreak(0)
                .allowPhoto(true)
                .build();
        if (status == ChallengeStatus.ACTIVE) {
            challenge.activate();
        }
        if (status == ChallengeStatus.ENDED) {
            challenge.end();
        }
        setBaseFields(challenge, id);
        return challenge;
    }

    private ChallengeMember challengeMember(Long id, Challenge challenge, Long userId, ChallengeMemberRole role) {
        ChallengeMember member = ChallengeMember.builder()
                .challenge(challenge)
                .userId(userId)
                .role(role)
                .build();
        setBaseFields(member, id);
        return member;
    }

    private ChallengeGroup group(Long id, Long ownerId) {
        ChallengeGroup group = ChallengeGroup.builder()
                .name("오운완 모임")
                .description("매일 운동 인증")
                .category(GroupCategory.EXERCISE)
                .mapType(MapType.GYM)
                .visibility(Visibility.PUBLIC)
                .maxMembers(6)
                .ownerId(ownerId)
                .build();
        setBaseFields(group, id);
        return group;
    }

    private void setBaseFields(Object target, Long id) {
        ReflectionTestUtils.setField(target, "id", id);
        ReflectionTestUtils.setField(target, "createdAt", LocalDateTime.of(2026, 9, 1, 12, 0));
        ReflectionTestUtils.setField(target, "updatedAt", LocalDateTime.of(2026, 9, 1, 12, 0));
    }

    private void assertBusinessException(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    @Nested
    @DisplayName("activateChallengesDueToday - 시작일 챌린지 활성화")
    class ActivateChallengesDueToday {

        @Test
        @DisplayName("첫 시즌 시작일이면 챌린지와 그룹을 ACTIVE로 변경한다")
        void activatesFirstSeasonAndGroup() {
            // given
            LocalDate today = LocalDate.now(KST);
            Challenge challenge = challenge(50L, 1, ChallengeStatus.READY, today, today.plusDays(6));
            ChallengeGroup group = group(12L, 1L);
            when(challengeRepository.findAllByStatus(ChallengeStatus.READY)).thenReturn(List.of(challenge));
            when(challengeGroupRepository.findById(12L)).thenReturn(Optional.of(group));

            // when
            challengeLifecycleService.activateChallengesDueToday();

            // then
            assertThat(challenge.getStatus()).isEqualTo(ChallengeStatus.ACTIVE);
            assertThat(group.getStatus()).isEqualTo(GroupStatus.ACTIVE);
            verify(challengeMemberRepository, never()).findByChallengeIdAndRole(50L, ChallengeMemberRole.OWNER);
        }

        @Test
        @DisplayName("시작일이 오늘이 아니면 변경하지 않는다")
        void skipsChallengeWhenStartDateIsNotToday() {
            // given
            LocalDate today = LocalDate.now(KST);
            Challenge challenge = challenge(50L, 1, ChallengeStatus.READY, today.plusDays(1), today.plusDays(7));
            when(challengeRepository.findAllByStatus(ChallengeStatus.READY)).thenReturn(List.of(challenge));

            // when
            challengeLifecycleService.activateChallengesDueToday();

            // then
            assertThat(challenge.getStatus()).isEqualTo(ChallengeStatus.READY);
            verify(challengeGroupRepository, never()).findById(12L);
        }

        @Test
        @DisplayName("연장 시즌 시작일이면 그룹 OWNER를 새 시즌 OWNER로 동기화한다")
        void activatesExtensionSeasonAndSyncsGroupOwner() {
            // given
            LocalDate today = LocalDate.now(KST);
            Challenge challenge = challenge(50L, 2, ChallengeStatus.READY, today, today.plusDays(6));
            ChallengeGroup group = group(12L, 1L);
            ChallengeMember owner = challengeMember(70L, challenge, 2L, ChallengeMemberRole.OWNER);
            when(challengeRepository.findAllByStatus(ChallengeStatus.READY)).thenReturn(List.of(challenge));
            when(challengeGroupRepository.findById(12L)).thenReturn(Optional.of(group));
            when(challengeMemberRepository.findByChallengeIdAndRole(50L, ChallengeMemberRole.OWNER))
                    .thenReturn(Optional.of(owner));

            // when
            challengeLifecycleService.activateChallengesDueToday();

            // then
            assertThat(challenge.getStatus()).isEqualTo(ChallengeStatus.ACTIVE);
            assertThat(group.getOwnerId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("그룹을 찾을 수 없으면 GROUP_NOT_FOUND")
        void throwsWhenGroupMissing() {
            // given
            LocalDate today = LocalDate.now(KST);
            Challenge challenge = challenge(50L, 1, ChallengeStatus.READY, today, today.plusDays(6));
            when(challengeRepository.findAllByStatus(ChallengeStatus.READY)).thenReturn(List.of(challenge));
            when(challengeGroupRepository.findById(12L)).thenReturn(Optional.empty());

            // when & then
            assertBusinessException(challengeLifecycleService::activateChallengesDueToday, ErrorCode.GROUP_NOT_FOUND);
        }

        @Test
        @DisplayName("연장 시즌 OWNER를 찾을 수 없으면 CHALLENGE_NOT_OWNER")
        void throwsWhenExtensionOwnerMissing() {
            // given
            LocalDate today = LocalDate.now(KST);
            Challenge challenge = challenge(50L, 2, ChallengeStatus.READY, today, today.plusDays(6));
            ChallengeGroup group = group(12L, 1L);
            when(challengeRepository.findAllByStatus(ChallengeStatus.READY)).thenReturn(List.of(challenge));
            when(challengeGroupRepository.findById(12L)).thenReturn(Optional.of(group));
            when(challengeMemberRepository.findByChallengeIdAndRole(50L, ChallengeMemberRole.OWNER))
                    .thenReturn(Optional.empty());

            // when & then
            assertBusinessException(
                    challengeLifecycleService::activateChallengesDueToday, ErrorCode.CHALLENGE_NOT_OWNER);
        }
    }

    @Nested
    @DisplayName("endChallengesDueToday - 종료일 지난 챌린지 종료")
    class EndChallengesDueToday {

        @Test
        @DisplayName("종료 대상이고 다음 시즌이 없으면 챌린지와 그룹을 ENDED로 변경한다")
        void endsChallengeAndGroupWhenNoNextSeason() {
            // given
            LocalDate today = LocalDate.now(KST);
            Challenge challenge = challenge(50L, 1, ChallengeStatus.ACTIVE, today.minusDays(7), today.minusDays(1));
            ChallengeGroup group = group(12L, 1L);
            group.activate();
            when(challengeRepository.findAllByStatus(ChallengeStatus.ACTIVE)).thenReturn(List.of(challenge));
            when(challengeRepository.findByGroupIdAndSeqNo(12L, 2)).thenReturn(Optional.empty());
            when(challengeGroupRepository.findById(12L)).thenReturn(Optional.of(group));

            // when
            challengeLifecycleService.endChallengesDueToday();

            // then
            assertThat(challenge.getStatus()).isEqualTo(ChallengeStatus.ENDED);
            assertThat(group.getStatus()).isEqualTo(GroupStatus.ENDED);
        }

        @Test
        @DisplayName("종료일 다음날이 아니면 변경하지 않는다")
        void skipsChallengeWhenEndDateIsNotYesterday() {
            // given
            LocalDate today = LocalDate.now(KST);
            Challenge challenge = challenge(50L, 1, ChallengeStatus.ACTIVE, today.minusDays(7), today);
            when(challengeRepository.findAllByStatus(ChallengeStatus.ACTIVE)).thenReturn(List.of(challenge));

            // when
            challengeLifecycleService.endChallengesDueToday();

            // then
            assertThat(challenge.getStatus()).isEqualTo(ChallengeStatus.ACTIVE);
            verify(challengeRepository, never()).findByGroupIdAndSeqNo(12L, 2);
        }

        @Test
        @DisplayName("다음 시즌이 있으면 그룹은 종료하지 않는다")
        void keepsGroupActiveWhenNextSeasonExists() {
            // given
            LocalDate today = LocalDate.now(KST);
            Challenge challenge = challenge(50L, 1, ChallengeStatus.ACTIVE, today.minusDays(7), today.minusDays(1));
            Challenge nextChallenge = challenge(51L, 2, ChallengeStatus.READY, today, today.plusDays(6));
            when(challengeRepository.findAllByStatus(ChallengeStatus.ACTIVE)).thenReturn(List.of(challenge));
            when(challengeRepository.findByGroupIdAndSeqNo(12L, 2)).thenReturn(Optional.of(nextChallenge));

            // when
            challengeLifecycleService.endChallengesDueToday();

            // then
            assertThat(challenge.getStatus()).isEqualTo(ChallengeStatus.ENDED);
            verify(challengeGroupRepository, never()).findById(12L);
        }

        @Test
        @DisplayName("그룹을 찾을 수 없으면 GROUP_NOT_FOUND")
        void throwsWhenGroupMissing() {
            // given
            LocalDate today = LocalDate.now(KST);
            Challenge challenge = challenge(50L, 1, ChallengeStatus.ACTIVE, today.minusDays(7), today.minusDays(1));
            when(challengeRepository.findAllByStatus(ChallengeStatus.ACTIVE)).thenReturn(List.of(challenge));
            when(challengeRepository.findByGroupIdAndSeqNo(12L, 2)).thenReturn(Optional.empty());
            when(challengeGroupRepository.findById(12L)).thenReturn(Optional.empty());

            // when & then
            assertBusinessException(challengeLifecycleService::endChallengesDueToday, ErrorCode.GROUP_NOT_FOUND);
        }
    }
}
