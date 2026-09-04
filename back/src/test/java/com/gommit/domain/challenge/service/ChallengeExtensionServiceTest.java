package com.gommit.domain.challenge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gommit.domain.challenge.dto.request.ExtensionChoiceRequest;
import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeMember;
import com.gommit.domain.challenge.entity.ChallengeMemberRole;
import com.gommit.domain.challenge.entity.ChallengeMemberStatus;
import com.gommit.domain.challenge.entity.ChallengeStatus;
import com.gommit.domain.challenge.entity.ExtensionChoice;
import com.gommit.domain.challenge.entity.FrequencyType;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.challenge.repository.ChallengeRepository;
import com.gommit.domain.group.entity.ChallengeGroup;
import com.gommit.domain.group.entity.GroupCategory;
import com.gommit.domain.group.entity.GroupMember;
import com.gommit.domain.group.entity.GroupMemberStatus;
import com.gommit.domain.group.entity.MapType;
import com.gommit.domain.group.entity.Visibility;
import com.gommit.domain.group.repository.GroupMemberRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChallengeExtensionServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Mock
    private ChallengeRepository challengeRepository;

    @Mock
    private ChallengeMemberRepository challengeMemberRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private ChallengeMemberService challengeMemberService;

    @InjectMocks
    private ChallengeExtensionService challengeExtensionService;

    private Challenge challenge(Long id, ChallengeStatus status) {
        LocalDate today = LocalDate.now(KST);
        Challenge challenge = Challenge.builder()
                .groupId(12L)
                .seqNo(1)
                .startDate(today.minusDays(5))
                .endDate(today.plusDays(5))
                .frequencyType(FrequencyType.DAILY)
                .frequencyValue(null)
                .daysOfWeek(null)
                .dailyCheckInCount(1)
                .requiredDayCount(11)
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

    private GroupMember groupMember(Long id, Long userId) {
        ChallengeGroup group = ChallengeGroup.builder()
                .name("오운완 모임")
                .description("매일 운동 인증")
                .category(GroupCategory.EXERCISE)
                .mapType(MapType.GYM)
                .visibility(Visibility.PUBLIC)
                .maxMembers(6)
                .ownerId(1L)
                .build();
        setBaseFields(group, 12L);
        GroupMember member = GroupMember.builder().group(group).userId(userId).build();
        setBaseFields(member, id);
        return member;
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
    @DisplayName("updateExtensionChoice - 연장 의사 선택")
    class UpdateExtensionChoice {

        @Test
        @DisplayName("ACTIVE 멤버가 마감 전 EXTEND를 선택하면 집계와 함께 반환한다")
        void updatesChoiceAndReturnsCounts() {
            // given
            Challenge challenge = challenge(50L, ChallengeStatus.ACTIVE);
            ChallengeMember member = challengeMember(70L, challenge, 2L, ChallengeMemberRole.MEMBER);
            when(challengeRepository.findById(50L)).thenReturn(Optional.of(challenge));
            when(challengeMemberRepository.findByChallengeIdAndUserId(50L, 2L)).thenReturn(Optional.of(member));
            when(challengeMemberRepository.countByChallengeIdAndStatusAndExtensionChoice(
                            50L, ChallengeMemberStatus.ACTIVE, ExtensionChoice.PENDING))
                    .thenReturn(1L);
            when(challengeMemberRepository.countByChallengeIdAndStatusAndExtensionChoice(
                            50L, ChallengeMemberStatus.ACTIVE, ExtensionChoice.EXTEND))
                    .thenReturn(2L);
            when(challengeMemberRepository.countByChallengeIdAndStatusAndExtensionChoice(
                            50L, ChallengeMemberStatus.ACTIVE, ExtensionChoice.DECLINE))
                    .thenReturn(3L);

            // when
            var response = challengeExtensionService.updateExtensionChoice(
                    50L, 2L, new ExtensionChoiceRequest(ExtensionChoice.EXTEND));

            // then
            assertThat(member.getExtensionChoice()).isEqualTo(ExtensionChoice.EXTEND);
            assertThat(response.choice()).isEqualTo(ExtensionChoice.EXTEND);
            assertThat(response.pendingCount()).isEqualTo(1);
            assertThat(response.extendCount()).isEqualTo(2);
            assertThat(response.declineCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("챌린지가 없으면 CHALLENGE_NOT_FOUND")
        void throwsWhenChallengeMissing() {
            // given
            when(challengeRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertBusinessException(
                    () -> challengeExtensionService.updateExtensionChoice(
                            999L, 2L, new ExtensionChoiceRequest(ExtensionChoice.EXTEND)),
                    ErrorCode.CHALLENGE_NOT_FOUND);
        }

        @Test
        @DisplayName("ACTIVE 챌린지가 아니면 EXTENSION_CHOICE_NOT_AVAILABLE")
        void throwsWhenChallengeIsNotActive() {
            // given
            Challenge challenge = challenge(50L, ChallengeStatus.READY);
            when(challengeRepository.findById(50L)).thenReturn(Optional.of(challenge));

            // when & then
            assertBusinessException(
                    () -> challengeExtensionService.updateExtensionChoice(
                            50L, 2L, new ExtensionChoiceRequest(ExtensionChoice.EXTEND)),
                    ErrorCode.EXTENSION_CHOICE_NOT_AVAILABLE);
        }

        @Test
        @DisplayName("챌린지 멤버가 아니면 CHALLENGE_NOT_MEMBER")
        void throwsWhenMemberMissing() {
            // given
            Challenge challenge = challenge(50L, ChallengeStatus.ACTIVE);
            when(challengeRepository.findById(50L)).thenReturn(Optional.of(challenge));
            when(challengeMemberRepository.findByChallengeIdAndUserId(50L, 2L)).thenReturn(Optional.empty());

            // when & then
            assertBusinessException(
                    () -> challengeExtensionService.updateExtensionChoice(
                            50L, 2L, new ExtensionChoiceRequest(ExtensionChoice.EXTEND)),
                    ErrorCode.CHALLENGE_NOT_MEMBER);
        }

        @Test
        @DisplayName("ACTIVE 멤버가 아니면 CHALLENGE_NOT_MEMBER")
        void throwsWhenMemberIsNotActive() {
            // given
            Challenge challenge = challenge(50L, ChallengeStatus.ACTIVE);
            ChallengeMember member = challengeMember(70L, challenge, 2L, ChallengeMemberRole.MEMBER);
            member.leave();
            when(challengeRepository.findById(50L)).thenReturn(Optional.of(challenge));
            when(challengeMemberRepository.findByChallengeIdAndUserId(50L, 2L)).thenReturn(Optional.of(member));

            // when & then
            assertBusinessException(
                    () -> challengeExtensionService.updateExtensionChoice(
                            50L, 2L, new ExtensionChoiceRequest(ExtensionChoice.EXTEND)),
                    ErrorCode.CHALLENGE_NOT_MEMBER);
        }

        @Test
        @DisplayName("연장 선택 기간이 지나면 EXTENSION_CHOICE_CLOSED")
        void throwsWhenChoicePeriodClosed() {
            // given
            Challenge challenge = challenge(50L, ChallengeStatus.ACTIVE);
            ReflectionTestUtils.setField(challenge, "endDate", LocalDate.now(KST).plusDays(1));
            ChallengeMember member = challengeMember(70L, challenge, 2L, ChallengeMemberRole.MEMBER);
            when(challengeRepository.findById(50L)).thenReturn(Optional.of(challenge));
            when(challengeMemberRepository.findByChallengeIdAndUserId(50L, 2L)).thenReturn(Optional.of(member));

            // when & then
            assertBusinessException(
                    () -> challengeExtensionService.updateExtensionChoice(
                            50L, 2L, new ExtensionChoiceRequest(ExtensionChoice.EXTEND)),
                    ErrorCode.EXTENSION_CHOICE_CLOSED);
        }

        @Test
        @DisplayName("EXTEND와 DECLINE 외 선택이면 INVALID_EXTENSION_CHOICE")
        void throwsWhenChoiceIsInvalid() {
            // given
            Challenge challenge = challenge(50L, ChallengeStatus.ACTIVE);
            ChallengeMember member = challengeMember(70L, challenge, 2L, ChallengeMemberRole.MEMBER);
            when(challengeRepository.findById(50L)).thenReturn(Optional.of(challenge));
            when(challengeMemberRepository.findByChallengeIdAndUserId(50L, 2L)).thenReturn(Optional.of(member));

            // when & then
            assertBusinessException(
                    () -> challengeExtensionService.updateExtensionChoice(
                            50L, 2L, new ExtensionChoiceRequest(ExtensionChoice.PENDING)),
                    ErrorCode.INVALID_EXTENSION_CHOICE);
        }
    }

    @Nested
    @DisplayName("finalizeExtension - 연장 확정")
    class FinalizeExtension {

        @Test
        @DisplayName("EXTEND 멤버가 없으면 다음 시즌을 만들지 않고 DECLINE 멤버를 그룹에서 내보낸다")
        void closesPendingAndLeavesDeclinedMembersWhenNoExtendMember() {
            // given
            Challenge challenge = challenge(50L, ChallengeStatus.ACTIVE);
            ChallengeMember pendingMember = challengeMember(70L, challenge, 2L, ChallengeMemberRole.MEMBER);
            ChallengeMember declinedMember = challengeMember(71L, challenge, 3L, ChallengeMemberRole.MEMBER);
            declinedMember.changeExtensionChoice(ExtensionChoice.DECLINE);
            GroupMember groupMember = groupMember(80L, 3L);
            when(challengeRepository.findById(50L)).thenReturn(Optional.of(challenge));
            when(challengeMemberRepository.findAllByChallengeIdAndStatusAndExtensionChoice(
                            50L, ChallengeMemberStatus.ACTIVE, ExtensionChoice.PENDING))
                    .thenReturn(List.of(pendingMember));
            when(challengeMemberRepository.findAllByChallengeIdAndStatusAndExtensionChoice(
                            50L, ChallengeMemberStatus.ACTIVE, ExtensionChoice.EXTEND))
                    .thenReturn(List.of());
            when(challengeMemberRepository.findAllByChallengeIdAndStatusAndExtensionChoice(
                            50L, ChallengeMemberStatus.ACTIVE, ExtensionChoice.DECLINE))
                    .thenReturn(List.of(declinedMember, pendingMember));
            when(groupMemberRepository.findByGroupIdAndUserId(12L, 3L)).thenReturn(Optional.of(groupMember));
            when(groupMemberRepository.findByGroupIdAndUserId(12L, 2L)).thenReturn(Optional.empty());

            // when
            challengeExtensionService.finalizeExtension(50L);

            // then
            assertThat(pendingMember.getExtensionChoice()).isEqualTo(ExtensionChoice.DECLINE);
            assertThat(groupMember.getStatus()).isEqualTo(GroupMemberStatus.LEFT);
            verify(challengeRepository, never()).save(any());
            verify(challengeMemberService, never()).createChallengeMember(any(), any(), any());
        }

        @Test
        @DisplayName("기존 OWNER가 연장하면 다음 시즌 OWNER로 유지한다")
        void createsNextChallengeWithCurrentOwner() {
            // given
            Challenge challenge = challenge(50L, ChallengeStatus.ACTIVE);
            ChallengeMember owner = challengeMember(70L, challenge, 1L, ChallengeMemberRole.OWNER);
            ChallengeMember member = challengeMember(71L, challenge, 2L, ChallengeMemberRole.MEMBER);
            owner.changeExtensionChoice(ExtensionChoice.EXTEND);
            member.changeExtensionChoice(ExtensionChoice.EXTEND);
            Challenge savedNextChallenge = challenge(51L, ChallengeStatus.READY);
            when(challengeRepository.findById(50L)).thenReturn(Optional.of(challenge));
            when(challengeMemberRepository.findAllByChallengeIdAndStatusAndExtensionChoice(
                            50L, ChallengeMemberStatus.ACTIVE, ExtensionChoice.PENDING))
                    .thenReturn(List.of());
            when(challengeMemberRepository.findAllByChallengeIdAndStatusAndExtensionChoice(
                            50L, ChallengeMemberStatus.ACTIVE, ExtensionChoice.EXTEND))
                    .thenReturn(List.of(owner, member));
            when(challengeMemberRepository.findByChallengeIdAndRole(50L, ChallengeMemberRole.OWNER))
                    .thenReturn(Optional.of(owner));
            when(challengeRepository.save(any())).thenReturn(savedNextChallenge);
            when(challengeMemberRepository.findAllByChallengeIdAndStatusAndExtensionChoice(
                            50L, ChallengeMemberStatus.ACTIVE, ExtensionChoice.DECLINE))
                    .thenReturn(List.of());

            // when
            challengeExtensionService.finalizeExtension(50L);

            // then
            ArgumentCaptor<Challenge> captor = ArgumentCaptor.forClass(Challenge.class);
            verify(challengeRepository).save(captor.capture());
            assertThat(captor.getValue().getSeqNo()).isEqualTo(2);
            assertThat(captor.getValue().getStartDate()).isEqualTo(challenge.getEndDate().plusDays(1));
            verify(challengeMemberService).createChallengeMember(savedNextChallenge, 1L, ChallengeMemberRole.OWNER);
            verify(challengeMemberService).createChallengeMember(savedNextChallenge, 2L, ChallengeMemberRole.MEMBER);
        }

        @Test
        @DisplayName("기존 OWNER가 연장하지 않으면 EXTEND 멤버가 다음 OWNER가 된다")
        void createsNextChallengeWithExtendMemberOwner() {
            // given
            Challenge challenge = challenge(50L, ChallengeStatus.ACTIVE);
            ChallengeMember owner = challengeMember(70L, challenge, 1L, ChallengeMemberRole.OWNER);
            ChallengeMember member = challengeMember(71L, challenge, 2L, ChallengeMemberRole.MEMBER);
            owner.changeExtensionChoice(ExtensionChoice.DECLINE);
            member.changeExtensionChoice(ExtensionChoice.EXTEND);
            Challenge savedNextChallenge = challenge(51L, ChallengeStatus.READY);
            when(challengeRepository.findById(50L)).thenReturn(Optional.of(challenge));
            when(challengeMemberRepository.findAllByChallengeIdAndStatusAndExtensionChoice(
                            50L, ChallengeMemberStatus.ACTIVE, ExtensionChoice.PENDING))
                    .thenReturn(List.of());
            when(challengeMemberRepository.findAllByChallengeIdAndStatusAndExtensionChoice(
                            50L, ChallengeMemberStatus.ACTIVE, ExtensionChoice.EXTEND))
                    .thenReturn(List.of(member));
            when(challengeMemberRepository.findByChallengeIdAndRole(50L, ChallengeMemberRole.OWNER))
                    .thenReturn(Optional.of(owner));
            when(challengeRepository.save(any())).thenReturn(savedNextChallenge);
            when(challengeMemberRepository.findAllByChallengeIdAndStatusAndExtensionChoice(
                            50L, ChallengeMemberStatus.ACTIVE, ExtensionChoice.DECLINE))
                    .thenReturn(List.of(owner));
            when(groupMemberRepository.findByGroupIdAndUserId(12L, 1L)).thenReturn(Optional.empty());

            // when
            challengeExtensionService.finalizeExtension(50L);

            // then
            verify(challengeMemberService).createChallengeMember(savedNextChallenge, 2L, ChallengeMemberRole.OWNER);
        }

        @Test
        @DisplayName("챌린지가 없으면 CHALLENGE_NOT_FOUND")
        void throwsWhenChallengeMissing() {
            // given
            when(challengeRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertBusinessException(() -> challengeExtensionService.finalizeExtension(999L), ErrorCode.CHALLENGE_NOT_FOUND);
        }

        @Test
        @DisplayName("현재 OWNER를 찾을 수 없으면 CHALLENGE_NOT_OWNER")
        void throwsWhenCurrentOwnerMissing() {
            // given
            Challenge challenge = challenge(50L, ChallengeStatus.ACTIVE);
            ChallengeMember member = challengeMember(71L, challenge, 2L, ChallengeMemberRole.MEMBER);
            member.changeExtensionChoice(ExtensionChoice.EXTEND);
            when(challengeRepository.findById(50L)).thenReturn(Optional.of(challenge));
            when(challengeMemberRepository.findAllByChallengeIdAndStatusAndExtensionChoice(
                            50L, ChallengeMemberStatus.ACTIVE, ExtensionChoice.PENDING))
                    .thenReturn(List.of());
            when(challengeMemberRepository.findAllByChallengeIdAndStatusAndExtensionChoice(
                            50L, ChallengeMemberStatus.ACTIVE, ExtensionChoice.EXTEND))
                    .thenReturn(List.of(member));
            when(challengeMemberRepository.findByChallengeIdAndRole(50L, ChallengeMemberRole.OWNER))
                    .thenReturn(Optional.empty());

            // when & then
            assertBusinessException(() -> challengeExtensionService.finalizeExtension(50L), ErrorCode.CHALLENGE_NOT_OWNER);
        }
    }

    @Nested
    @DisplayName("finalizeExtensionsDueToday - 오늘 마감 연장 확정")
    class FinalizeExtensionsDueToday {

        @Test
        @DisplayName("KST 기준 오늘이 마감일인 챌린지만 연장 확정한다")
        void finalizesOnlyChallengesDueToday() {
            // given
            LocalDate today = LocalDate.now(KST);
            Challenge dueChallenge = challenge(50L, ChallengeStatus.ACTIVE);
            ReflectionTestUtils.setField(dueChallenge, "endDate", today.plusDays(2));
            Challenge laterChallenge = challenge(51L, ChallengeStatus.ACTIVE);
            ReflectionTestUtils.setField(laterChallenge, "endDate", today.plusDays(3));
            when(challengeRepository.findAllByStatus(ChallengeStatus.ACTIVE))
                    .thenReturn(List.of(dueChallenge, laterChallenge));
            when(challengeRepository.findById(50L)).thenReturn(Optional.of(dueChallenge));
            when(challengeMemberRepository.findAllByChallengeIdAndStatusAndExtensionChoice(
                            50L, ChallengeMemberStatus.ACTIVE, ExtensionChoice.PENDING))
                    .thenReturn(List.of());
            when(challengeMemberRepository.findAllByChallengeIdAndStatusAndExtensionChoice(
                            50L, ChallengeMemberStatus.ACTIVE, ExtensionChoice.EXTEND))
                    .thenReturn(List.of());
            when(challengeMemberRepository.findAllByChallengeIdAndStatusAndExtensionChoice(
                            50L, ChallengeMemberStatus.ACTIVE, ExtensionChoice.DECLINE))
                    .thenReturn(List.of());

            // when
            challengeExtensionService.finalizeExtensionsDueToday();

            // then
            verify(challengeRepository).findById(50L);
            verify(challengeRepository, never()).findById(51L);
        }
    }
}
