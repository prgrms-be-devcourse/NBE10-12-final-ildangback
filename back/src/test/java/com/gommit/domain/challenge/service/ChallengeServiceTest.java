package com.gommit.domain.challenge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gommit.domain.challenge.dto.request.ChallengeUpdateRequest;
import com.gommit.domain.challenge.dto.request.InitialChallengeSettingRequest;
import com.gommit.domain.challenge.dto.request.OwnerDelegationRequest;
import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeMember;
import com.gommit.domain.challenge.entity.ChallengeMemberRole;
import com.gommit.domain.challenge.entity.ChallengeMemberStatus;
import com.gommit.domain.challenge.entity.ChallengeStatus;
import com.gommit.domain.challenge.entity.FrequencyType;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.challenge.repository.ChallengeRepository;
import com.gommit.domain.checkin.entity.CheckInType;
import com.gommit.domain.checkin.repository.CheckInRepository;
import com.gommit.domain.group.entity.ChallengeGroup;
import com.gommit.domain.group.entity.GroupCategory;
import com.gommit.domain.group.entity.MapType;
import com.gommit.domain.group.entity.Visibility;
import com.gommit.domain.group.repository.ChallengeGroupRepository;
import com.gommit.domain.user.entity.User;
import com.gommit.domain.user.repository.UserRepository;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
class ChallengeServiceTest {

    @Mock
    private ChallengeRepository challengeRepository;

    @Mock
    private ChallengeMemberRepository challengeMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CheckInRepository checkInRepository;

    @Mock
    private ChallengeGroupRepository challengeGroupRepository;

    @Mock
    private ChallengeMemberService challengeMemberService;

    @Mock
    private ChallengeProgressCalculator challengeProgressCalculator;

    @InjectMocks
    private ChallengeService challengeService;

    private InitialChallengeSettingRequest initialSetting() {
        return new InitialChallengeSettingRequest(
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(7),
                FrequencyType.DAILY,
                null,
                null,
                2,
                List.of(CheckInType.PHOTO));
    }

    private ChallengeUpdateRequest updateRequest() {
        return new ChallengeUpdateRequest(
                LocalDate.now().plusDays(2),
                LocalDate.now().plusDays(10),
                FrequencyType.EVERY_N_DAYS,
                2,
                null,
                3,
                List.of(CheckInType.PHOTO));
    }

    private Challenge challenge(Long id, ChallengeStatus status) {
        Challenge challenge = Challenge.builder()
                .groupId(12L)
                .seqNo(1)
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

    private User user(Long id, String nickname) {
        User user = new User(nickname + "@example.com", "encoded", nickname);
        setBaseFields(user, id);
        return user;
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
    @DisplayName("createInitialChallenge - 첫 챌린지 생성")
    class CreateInitialChallenge {

        @Test
        @DisplayName("설정이 유효하면 챌린지를 저장하고 OWNER 멤버를 만든다")
        void createsInitialChallengeAndOwnerMember() {
            // given
            InitialChallengeSettingRequest setting = initialSetting();
            Challenge savedChallenge = challenge(50L, ChallengeStatus.READY);
            when(challengeRepository.save(any())).thenReturn(savedChallenge);

            // when
            Challenge response = challengeService.createInitialChallenge(12L, 1L, setting);

            // then
            ArgumentCaptor<Challenge> captor = ArgumentCaptor.forClass(Challenge.class);
            verify(challengeRepository).save(captor.capture());
            assertThat(captor.getValue().getGroupId()).isEqualTo(12L);
            assertThat(captor.getValue().getSeqNo()).isEqualTo(1);
            assertThat(captor.getValue().getRequiredDayCount()).isEqualTo(7);
            assertThat(captor.getValue().isAllowPhoto()).isTrue();
            verify(challengeMemberService).createChallengeMember(savedChallenge, 1L, ChallengeMemberRole.OWNER);
            assertThat(response.getId()).isEqualTo(50L);
        }

        @Test
        @DisplayName("시작일이 오늘이면 INVALID_START_DATE")
        void throwsWhenStartDateIsNotFuture() {
            // given
            InitialChallengeSettingRequest setting = new InitialChallengeSettingRequest(
                    LocalDate.now(),
                    LocalDate.now().plusDays(7),
                    FrequencyType.DAILY,
                    null,
                    null,
                    1,
                    List.of(CheckInType.PHOTO));

            // when & then
            assertBusinessException(
                    () -> challengeService.createInitialChallenge(12L, 1L, setting), ErrorCode.INVALID_START_DATE);
            verify(challengeRepository, never()).save(any());
        }

        @Test
        @DisplayName("인증 방식이 비어 있으면 NO_CHECK_IN_METHOD")
        void throwsWhenAllowedTypesIsEmpty() {
            // given
            InitialChallengeSettingRequest setting = new InitialChallengeSettingRequest(
                    LocalDate.now().plusDays(1),
                    LocalDate.now().plusDays(7),
                    FrequencyType.DAILY,
                    null,
                    null,
                    1,
                    List.of());

            // when & then
            assertBusinessException(
                    () -> challengeService.createInitialChallenge(12L, 1L, setting), ErrorCode.NO_CHECK_IN_METHOD);
            verify(challengeRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getChallengeStatus - 챌린지 현황")
    class GetChallengeStatus {

        @Test
        @DisplayName("챌린지와 멤버 권한을 확인하고 진행 현황을 반환한다")
        void returnsChallengeStatus() {
            // given
            Challenge challenge = challenge(50L, ChallengeStatus.ACTIVE);
            ChallengeMember member = challengeMember(70L, challenge, 2L, ChallengeMemberRole.MEMBER);
            ChallengeMember owner = challengeMember(71L, challenge, 1L, ChallengeMemberRole.OWNER);
            when(challengeRepository.findById(50L)).thenReturn(Optional.of(challenge));
            when(challengeMemberRepository.findByChallengeIdAndUserId(50L, 2L)).thenReturn(Optional.of(member));
            when(challengeMemberRepository.findByChallengeIdAndRole(50L, ChallengeMemberRole.OWNER))
                    .thenReturn(Optional.of(owner));
            when(challengeMemberRepository.countByChallengeIdAndStatus(50L, ChallengeMemberStatus.ACTIVE))
                    .thenReturn(3L);
            when(challengeProgressCalculator.calculateCurrentDay(eq(challenge), any(LocalDate.class)))
                    .thenReturn(2);
            when(challengeProgressCalculator.calculatePeriodProgressRate(2, 7)).thenReturn(28.6);

            // when
            var response = challengeService.getChallengeStatus(50L, 2L);

            // then
            assertThat(response.challenge().id()).isEqualTo(50L);
            assertThat(response.challenge().ownerId()).isEqualTo(1L);
            assertThat(response.currentDay()).isEqualTo(2);
            assertThat(response.totalDays()).isEqualTo(7);
            assertThat(response.participantCount()).isEqualTo(3);
            assertThat(response.periodProgressRate()).isEqualTo(28.6);
        }

        @Test
        @DisplayName("챌린지가 없으면 CHALLENGE_NOT_FOUND")
        void throwsWhenChallengeNotFound() {
            // given
            when(challengeRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertBusinessException(() -> challengeService.getChallengeStatus(999L, 2L), ErrorCode.CHALLENGE_NOT_FOUND);
        }

        @Test
        @DisplayName("요청자가 시즌 멤버가 아니면 CHALLENGE_NOT_MEMBER")
        void throwsWhenUserIsNotChallengeMember() {
            // given
            Challenge challenge = challenge(50L, ChallengeStatus.ACTIVE);
            when(challengeRepository.findById(50L)).thenReturn(Optional.of(challenge));
            when(challengeMemberRepository.findByChallengeIdAndUserId(50L, 2L)).thenReturn(Optional.empty());

            // when & then
            assertBusinessException(() -> challengeService.getChallengeStatus(50L, 2L), ErrorCode.CHALLENGE_NOT_MEMBER);
        }
    }

    @Nested
    @DisplayName("getMemberTodayStatuses - 시즌 멤버 오늘 인증 현황")
    class GetMemberTodayStatuses {

        @Test
        @DisplayName("참여 중인 멤버들의 닉네임과 오늘 인증 횟수를 반환한다")
        void returnsMemberTodayStatuses() {
            // given
            Challenge challenge = challenge(50L, ChallengeStatus.ACTIVE);
            ChallengeMember requester = challengeMember(70L, challenge, 1L, ChallengeMemberRole.OWNER);
            ChallengeMember member = challengeMember(71L, challenge, 2L, ChallengeMemberRole.MEMBER);
            when(challengeRepository.findById(50L)).thenReturn(Optional.of(challenge));
            when(challengeMemberRepository.findByChallengeIdAndUserId(50L, 1L)).thenReturn(Optional.of(requester));
            when(challengeMemberRepository.findAllByChallengeIdAndStatus(50L, ChallengeMemberStatus.ACTIVE))
                    .thenReturn(List.of(requester, member));
            when(userRepository.findAllByIdIn(List.of(1L, 2L))).thenReturn(List.of(user(1L, "방장"), user(2L, "멤버")));

            // when
            var response = challengeService.getMemberTodayStatuses(50L, 1L);

            // then
            assertThat(response).hasSize(2);
            assertThat(response.get(0).nickname()).isEqualTo("방장");
            assertThat(response.get(0).todayCheckInCount()).isZero();
            assertThat(response.get(1).nickname()).isEqualTo("멤버");
        }
    }

    @Nested
    @DisplayName("updateChallenge - 챌린지 설정 수정")
    class UpdateChallenge {

        @Test
        @DisplayName("OWNER가 READY 챌린지 설정을 수정하면 변경값을 반환한다")
        void updatesReadyChallengeByOwner() {
            // given
            Challenge challenge = challenge(50L, ChallengeStatus.READY);
            ChallengeMember owner = challengeMember(70L, challenge, 1L, ChallengeMemberRole.OWNER);
            ChallengeUpdateRequest request = updateRequest();
            when(challengeRepository.findById(50L)).thenReturn(Optional.of(challenge));
            when(challengeMemberRepository.findByChallengeIdAndUserId(50L, 1L)).thenReturn(Optional.of(owner));

            // when
            var response = challengeService.updateChallenge(50L, 1L, request);

            // then
            assertThat(response.id()).isEqualTo(50L);
            assertThat(response.frequencyType()).isEqualTo(FrequencyType.EVERY_N_DAYS);
            assertThat(response.frequencyValue()).isEqualTo(2);
            assertThat(response.dailyCheckInCount()).isEqualTo(3);
            assertThat(challenge.getRequiredDayCount()).isEqualTo(5);
            assertThat(challenge.isAllowPhoto()).isTrue();
        }

        @Test
        @DisplayName("OWNER가 아니면 CHALLENGE_NOT_OWNER")
        void throwsWhenUserIsNotOwner() {
            // given
            Challenge challenge = challenge(50L, ChallengeStatus.READY);
            ChallengeMember member = challengeMember(70L, challenge, 2L, ChallengeMemberRole.MEMBER);
            when(challengeRepository.findById(50L)).thenReturn(Optional.of(challenge));
            when(challengeMemberRepository.findByChallengeIdAndUserId(50L, 2L)).thenReturn(Optional.of(member));

            // when & then
            assertBusinessException(
                    () -> challengeService.updateChallenge(50L, 2L, updateRequest()), ErrorCode.CHALLENGE_NOT_OWNER);
        }

        @Test
        @DisplayName("READY 상태가 아니면 CHALLENGE_NOT_EDITABLE")
        void throwsWhenChallengeIsNotReady() {
            // given
            Challenge challenge = challenge(50L, ChallengeStatus.ACTIVE);
            ChallengeMember owner = challengeMember(70L, challenge, 1L, ChallengeMemberRole.OWNER);
            when(challengeRepository.findById(50L)).thenReturn(Optional.of(challenge));
            when(challengeMemberRepository.findByChallengeIdAndUserId(50L, 1L)).thenReturn(Optional.of(owner));

            // when & then
            assertBusinessException(
                    () -> challengeService.updateChallenge(50L, 1L, updateRequest()), ErrorCode.CHALLENGE_NOT_EDITABLE);
        }

        @Test
        @DisplayName("하루 인증 횟수가 범위를 벗어나면 INVALID_DAILY_COUNT")
        void throwsWhenDailyCheckInCountIsInvalid() {
            // given
            Challenge challenge = challenge(50L, ChallengeStatus.READY);
            ChallengeMember owner = challengeMember(70L, challenge, 1L, ChallengeMemberRole.OWNER);
            ChallengeUpdateRequest request = new ChallengeUpdateRequest(
                    LocalDate.now().plusDays(2),
                    LocalDate.now().plusDays(10),
                    FrequencyType.DAILY,
                    null,
                    null,
                    11,
                    List.of(CheckInType.PHOTO));
            when(challengeRepository.findById(50L)).thenReturn(Optional.of(challenge));
            when(challengeMemberRepository.findByChallengeIdAndUserId(50L, 1L)).thenReturn(Optional.of(owner));

            // when & then
            assertBusinessException(
                    () -> challengeService.updateChallenge(50L, 1L, request), ErrorCode.INVALID_DAILY_COUNT);
        }

        @Test
        @DisplayName("연장 시즌은 시작일을 변경할 수 없다")
        void throwsWhenExtensionStartDateIsChanged() {
            // given
            Challenge challenge = challenge(50L, ChallengeStatus.READY);
            ReflectionTestUtils.setField(challenge, "seqNo", 2);
            ChallengeMember owner = challengeMember(70L, challenge, 1L, ChallengeMemberRole.OWNER);
            when(challengeRepository.findById(50L)).thenReturn(Optional.of(challenge));
            when(challengeMemberRepository.findByChallengeIdAndUserId(50L, 1L)).thenReturn(Optional.of(owner));

            // when & then
            assertBusinessException(
                    () -> challengeService.updateChallenge(50L, 1L, updateRequest()),
                    ErrorCode.EXTENSION_START_DATE_NOT_EDITABLE);
        }
    }

    @Nested
    @DisplayName("delegateOwner - OWNER 위임")
    class DelegateOwner {

        @Test
        @DisplayName("OWNER를 다른 ACTIVE 멤버에게 위임하고 첫 시즌 그룹 OWNER도 변경한다")
        void delegatesOwnerToActiveMember() {
            // given
            Challenge challenge = challenge(50L, ChallengeStatus.READY);
            ChallengeMember currentOwner = challengeMember(70L, challenge, 1L, ChallengeMemberRole.OWNER);
            ChallengeMember targetMember = challengeMember(71L, challenge, 2L, ChallengeMemberRole.MEMBER);
            ChallengeGroup group = group(12L, 1L);
            when(challengeRepository.findById(50L)).thenReturn(Optional.of(challenge));
            when(challengeMemberRepository.findByChallengeIdAndUserId(50L, 1L)).thenReturn(Optional.of(currentOwner));
            when(challengeMemberRepository.findByChallengeIdAndUserId(50L, 2L)).thenReturn(Optional.of(targetMember));
            when(challengeGroupRepository.findById(12L)).thenReturn(Optional.of(group));

            // when
            var response = challengeService.delegateOwner(50L, 1L, new OwnerDelegationRequest(2L));

            // then
            assertThat(currentOwner.getRole()).isEqualTo(ChallengeMemberRole.MEMBER);
            assertThat(targetMember.getRole()).isEqualTo(ChallengeMemberRole.OWNER);
            assertThat(group.getOwnerId()).isEqualTo(2L);
            assertThat(response.previousOwnerId()).isEqualTo(1L);
            assertThat(response.newOwnerId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("자기 자신에게 위임하면 CANNOT_DELEGATE_TO_SELF")
        void throwsWhenDelegatingToSelf() {
            // given
            Challenge challenge = challenge(50L, ChallengeStatus.READY);
            ChallengeMember currentOwner = challengeMember(70L, challenge, 1L, ChallengeMemberRole.OWNER);
            when(challengeRepository.findById(50L)).thenReturn(Optional.of(challenge));
            when(challengeMemberRepository.findByChallengeIdAndUserId(50L, 1L)).thenReturn(Optional.of(currentOwner));

            // when & then
            assertBusinessException(
                    () -> challengeService.delegateOwner(50L, 1L, new OwnerDelegationRequest(1L)),
                    ErrorCode.CANNOT_DELEGATE_TO_SELF);
        }

        @Test
        @DisplayName("위임 대상이 ACTIVE 멤버가 아니면 CHALLENGE_NOT_MEMBER")
        void throwsWhenTargetMemberIsNotActive() {
            // given
            Challenge challenge = challenge(50L, ChallengeStatus.READY);
            ChallengeMember currentOwner = challengeMember(70L, challenge, 1L, ChallengeMemberRole.OWNER);
            ChallengeMember targetMember = challengeMember(71L, challenge, 2L, ChallengeMemberRole.MEMBER);
            targetMember.leave();
            when(challengeRepository.findById(50L)).thenReturn(Optional.of(challenge));
            when(challengeMemberRepository.findByChallengeIdAndUserId(50L, 1L)).thenReturn(Optional.of(currentOwner));
            when(challengeMemberRepository.findByChallengeIdAndUserId(50L, 2L)).thenReturn(Optional.of(targetMember));

            // when & then
            assertBusinessException(
                    () -> challengeService.delegateOwner(50L, 1L, new OwnerDelegationRequest(2L)),
                    ErrorCode.CHALLENGE_NOT_MEMBER);
            assertThat(currentOwner.getRole()).isEqualTo(ChallengeMemberRole.OWNER);
        }
    }
}
