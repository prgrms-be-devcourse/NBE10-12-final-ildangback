package com.gommit.domain.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gommit.domain.challenge.dto.request.InitialChallengeSettingRequest;
import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeMember;
import com.gommit.domain.challenge.entity.ChallengeMemberRole;
import com.gommit.domain.challenge.entity.ChallengeMemberStatus;
import com.gommit.domain.challenge.entity.ChallengeStatus;
import com.gommit.domain.challenge.entity.FrequencyType;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.challenge.repository.ChallengeRepository;
import com.gommit.domain.challenge.service.ChallengeMemberService;
import com.gommit.domain.challenge.service.ChallengeProgressCalculator;
import com.gommit.domain.challenge.service.ChallengeService;
import com.gommit.domain.checkin.entity.CheckInType;
import com.gommit.domain.checkin.repository.CheckInRepository;
import com.gommit.domain.group.dto.request.GroupCreateRequest;
import com.gommit.domain.group.entity.ChallengeGroup;
import com.gommit.domain.group.entity.GroupCategory;
import com.gommit.domain.group.entity.GroupMember;
import com.gommit.domain.group.entity.GroupMemberStatus;
import com.gommit.domain.group.entity.GroupSort;
import com.gommit.domain.group.entity.GroupStatus;
import com.gommit.domain.group.entity.MapType;
import com.gommit.domain.group.entity.Visibility;
import com.gommit.domain.group.repository.ChallengeGroupRepository;
import com.gommit.domain.group.repository.GroupMemberCount;
import com.gommit.domain.group.repository.GroupMemberRepository;
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
class GroupServiceTest {

    @Mock
    private ChallengeGroupRepository challengeGroupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private ChallengeService challengeService;

    @Mock
    private ChallengeRepository challengeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChallengeMemberRepository challengeMemberRepository;

    @Mock
    private CheckInRepository checkInRepository;

    @Mock
    private ChallengeMemberService challengeMemberService;

    @Mock
    private ChallengeProgressCalculator challengeProgressCalculator;

    @InjectMocks
    private GroupService groupService;

    private GroupCreateRequest createRequest(GroupCategory category, MapType mapType) {
        return new GroupCreateRequest("오운완 모임", "매일 운동 인증", category, mapType, Visibility.PUBLIC, 6, initialSetting());
    }

    private InitialChallengeSettingRequest initialSetting() {
        return new InitialChallengeSettingRequest(
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(7),
                FrequencyType.DAILY,
                null,
                null,
                1,
                List.of(CheckInType.PHOTO));
    }

    private ChallengeGroup group(Long id, String name, GroupCategory category, Visibility visibility, int maxMembers) {
        ChallengeGroup group = ChallengeGroup.builder()
                .name(name)
                .description("함께 인증하는 그룹")
                .category(category)
                .mapType(category == GroupCategory.EXERCISE ? MapType.GYM : MapType.STUDY_ROOM)
                .visibility(visibility)
                .maxMembers(maxMembers)
                .ownerId(1L)
                .build();
        setBaseFields(group, id);
        return group;
    }

    private Challenge challenge(Long id, Long groupId, ChallengeStatus status) {
        Challenge challenge = Challenge.builder()
                .groupId(groupId)
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

    private GroupMember groupMember(Long id, ChallengeGroup group, Long userId) {
        GroupMember member = GroupMember.builder().group(group).userId(userId).build();
        setBaseFields(member, id);
        return member;
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

    private User user(Long id, String nickname) {
        User user = new User(nickname + "@example.com", "encoded", nickname);
        setBaseFields(user, id);
        return user;
    }

    private GroupMemberCount memberCount(Long groupId, Long count) {
        return new GroupMemberCount() {
            @Override
            public Long getGroupId() {
                return groupId;
            }

            @Override
            public Long getCount() {
                return count;
            }
        };
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
    @DisplayName("createGroup - 그룹 생성")
    class CreateGroup {

        @Test
        @DisplayName("그룹과 생성자 멤버를 저장하고 첫 챌린지를 만든다")
        void createsGroupWithOwnerMemberAndInitialChallenge() {
            // given
            GroupCreateRequest request = createRequest(GroupCategory.EXERCISE, MapType.GYM);
            ChallengeGroup savedGroup = group(12L, "오운완 모임", GroupCategory.EXERCISE, Visibility.PUBLIC, 6);
            GroupMember savedMember = groupMember(30L, savedGroup, 1L);
            Challenge savedChallenge = challenge(50L, 12L, ChallengeStatus.READY);
            when(challengeGroupRepository.save(any())).thenReturn(savedGroup);
            when(groupMemberRepository.save(any())).thenReturn(savedMember);
            when(challengeService.createInitialChallenge(12L, 1L, request.challenge()))
                    .thenReturn(savedChallenge);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "꼬밋러")));

            // when
            var response = groupService.createGroup(1L, request);

            // then
            ArgumentCaptor<ChallengeGroup> groupCaptor = ArgumentCaptor.forClass(ChallengeGroup.class);
            verify(challengeGroupRepository).save(groupCaptor.capture());
            assertThat(groupCaptor.getValue().getName()).isEqualTo("오운완 모임");
            assertThat(groupCaptor.getValue().getStatus()).isEqualTo(GroupStatus.READY);
            verify(groupMemberRepository).save(any(GroupMember.class));
            verify(challengeService).createInitialChallenge(12L, 1L, request.challenge());
            assertThat(response.group().id()).isEqualTo(12L);
            assertThat(response.currentChallenge().id()).isEqualTo(50L);
            assertThat(response.members()).hasSize(1);
        }

        @Test
        @DisplayName("카테고리와 맵 타입 조합이 맞지 않으면 INVALID_CATEGORY_MAP_TYPE")
        void throwsWhenCategoryMapTypeIsInvalid() {
            // given
            GroupCreateRequest request = createRequest(GroupCategory.EXERCISE, MapType.STUDY_ROOM);

            // when & then
            assertBusinessException(() -> groupService.createGroup(1L, request), ErrorCode.INVALID_CATEGORY_MAP_TYPE);
            verify(challengeGroupRepository, never()).save(any());
            verify(groupMemberRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getPublicGroups - 공개 그룹 목록 조회")
    class GetPublicGroups {

        @Test
        @DisplayName("조회 결과가 없으면 빈 목록과 hasNext=false를 반환한다")
        void returnsEmptyWhenNoGroups() {
            // given
            when(challengeGroupRepository.findAllByVisibilityAndStatus(Visibility.PUBLIC, GroupStatus.READY))
                    .thenReturn(List.of());

            // when
            var response = groupService.getPublicGroups(null, null, GroupSort.LATEST, null, 20);

            // then
            assertThat(response.content()).isEmpty();
            assertThat(response.hasNext()).isFalse();
            assertThat(response.nextCursor()).isNull();
        }

        @Test
        @DisplayName("READY 챌린지가 없으면 CHALLENGE_NOT_FOUND")
        void throwsWhenReadyChallengeMissing() {
            // given
            ChallengeGroup group = group(12L, "오운완 모임", GroupCategory.EXERCISE, Visibility.PUBLIC, 6);
            when(challengeGroupRepository.findAllByVisibilityAndStatus(Visibility.PUBLIC, GroupStatus.READY))
                    .thenReturn(List.of(group));
            when(challengeRepository.findAllByGroupIdInAndStatus(List.of(12L), ChallengeStatus.READY))
                    .thenReturn(List.of());
            when(groupMemberRepository.countByGroupIdsAndStatus(List.of(12L), GroupMemberStatus.ACTIVE))
                    .thenReturn(List.of());

            // when & then
            assertBusinessException(
                    () -> groupService.getPublicGroups(null, null, GroupSort.LATEST, null, 20),
                    ErrorCode.CHALLENGE_NOT_FOUND);
        }

        @Test
        @DisplayName("키워드와 카테고리로 걸러 최신순 커서 응답을 반환한다")
        void filtersAndPaginatesLatestGroups() {
            // given
            ChallengeGroup first = group(11L, "아침 오운완", GroupCategory.EXERCISE, Visibility.PUBLIC, 6);
            ChallengeGroup second = group(12L, "저녁 오운완", GroupCategory.EXERCISE, Visibility.PUBLIC, 6);
            ChallengeGroup ignored = group(13L, "독서 모임", GroupCategory.READING, Visibility.PUBLIC, 6);
            Challenge firstChallenge = challenge(101L, 11L, ChallengeStatus.READY);
            Challenge secondChallenge = challenge(102L, 12L, ChallengeStatus.READY);
            when(challengeGroupRepository.findAllByVisibilityAndStatus(Visibility.PUBLIC, GroupStatus.READY))
                    .thenReturn(List.of(first, second, ignored));
            when(challengeRepository.findAllByGroupIdInAndStatus(List.of(11L, 12L), ChallengeStatus.READY))
                    .thenReturn(List.of(firstChallenge, secondChallenge));
            when(groupMemberRepository.countByGroupIdsAndStatus(List.of(11L, 12L), GroupMemberStatus.ACTIVE))
                    .thenReturn(List.of(memberCount(11L, 2L), memberCount(12L, 4L)));

            // when
            var response = groupService.getPublicGroups("오운완", GroupCategory.EXERCISE, GroupSort.LATEST, null, 1);

            // then
            assertThat(response.content()).hasSize(1);
            assertThat(response.content().get(0).id()).isEqualTo(12L);
            assertThat(response.content().get(0).currentMembers()).isEqualTo(4);
            assertThat(response.hasNext()).isTrue();
            assertThat(response.nextCursor()).isEqualTo(12L);
        }
    }

    @Nested
    @DisplayName("getGroupDetail - 그룹 상세 조회")
    class GetGroupDetail {

        @Test
        @DisplayName("그룹과 현재 챌린지와 멤버 목록을 반환한다")
        void returnsGroupDetail() {
            // given
            ChallengeGroup group = group(12L, "오운완 모임", GroupCategory.EXERCISE, Visibility.PUBLIC, 6);
            Challenge challenge = challenge(50L, 12L, ChallengeStatus.ACTIVE);
            GroupMember member = groupMember(30L, group, 1L);
            when(challengeGroupRepository.findById(12L)).thenReturn(Optional.of(group));
            when(challengeRepository.findFirstByGroupIdAndStatus(12L, ChallengeStatus.ACTIVE))
                    .thenReturn(Optional.of(challenge));
            when(groupMemberRepository.findAllByGroupIdAndStatus(12L, GroupMemberStatus.ACTIVE))
                    .thenReturn(List.of(member));
            when(userRepository.findAllById(List.of(1L))).thenReturn(List.of(user(1L, "꼬밋러")));

            // when
            var response = groupService.getGroupDetail(12L);

            // then
            assertThat(response.group().id()).isEqualTo(12L);
            assertThat(response.currentChallenge().id()).isEqualTo(50L);
            assertThat(response.members()).hasSize(1);
            assertThat(response.members().get(0).nickname()).isEqualTo("꼬밋러");
        }

        @Test
        @DisplayName("그룹이 없으면 GROUP_NOT_FOUND")
        void throwsWhenGroupNotFound() {
            // given
            when(challengeGroupRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertBusinessException(() -> groupService.getGroupDetail(999L), ErrorCode.GROUP_NOT_FOUND);
        }

        @Test
        @DisplayName("멤버의 유저 정보가 없으면 USER_NOT_FOUND")
        void throwsWhenMemberUserMissing() {
            // given
            ChallengeGroup group = group(12L, "오운완 모임", GroupCategory.EXERCISE, Visibility.PUBLIC, 6);
            GroupMember member = groupMember(30L, group, 1L);
            when(challengeGroupRepository.findById(12L)).thenReturn(Optional.of(group));
            when(challengeRepository.findFirstByGroupIdAndStatus(12L, ChallengeStatus.ACTIVE))
                    .thenReturn(Optional.empty());
            when(challengeRepository.findFirstByGroupIdAndStatus(12L, ChallengeStatus.READY))
                    .thenReturn(Optional.empty());
            when(groupMemberRepository.findAllByGroupIdAndStatus(12L, GroupMemberStatus.ACTIVE))
                    .thenReturn(List.of(member));
            when(userRepository.findAllById(List.of(1L))).thenReturn(List.of());

            // when & then
            assertBusinessException(() -> groupService.getGroupDetail(12L), ErrorCode.USER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("joinGroup - 공개 그룹 참여")
    class JoinGroup {

        @Test
        @DisplayName("참여 가능하면 그룹 멤버와 챌린지 멤버를 만들고 응답한다")
        void joinsPublicReadyGroup() {
            // given
            ChallengeGroup group = group(12L, "오운완 모임", GroupCategory.EXERCISE, Visibility.PUBLIC, 6);
            Challenge challenge = challenge(50L, 12L, ChallengeStatus.READY);
            GroupMember savedGroupMember = groupMember(30L, group, 2L);
            ChallengeMember savedChallengeMember = challengeMember(70L, challenge, 2L, ChallengeMemberRole.MEMBER);
            when(challengeGroupRepository.findById(12L)).thenReturn(Optional.of(group));
            when(challengeRepository.findFirstByGroupIdAndStatus(12L, ChallengeStatus.READY))
                    .thenReturn(Optional.of(challenge));
            when(groupMemberRepository.existsByGroupIdAndUserId(12L, 2L)).thenReturn(false);
            when(groupMemberRepository.countByGroupIdAndStatus(12L, GroupMemberStatus.ACTIVE))
                    .thenReturn(1L);
            when(groupMemberRepository.save(any())).thenReturn(savedGroupMember);
            when(challengeMemberService.createChallengeMember(challenge, 2L, ChallengeMemberRole.MEMBER))
                    .thenReturn(savedChallengeMember);
            when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "새멤버")));

            // when
            var response = groupService.joinGroup(12L, 2L);

            // then
            verify(groupMemberRepository).save(any(GroupMember.class));
            verify(challengeMemberService).createChallengeMember(challenge, 2L, ChallengeMemberRole.MEMBER);
            assertThat(response.groupMember().userId()).isEqualTo(2L);
            assertThat(response.challengeId()).isEqualTo(50L);
            assertThat(response.challengeMemberId()).isEqualTo(70L);
        }

        @Test
        @DisplayName("그룹이 없으면 GROUP_NOT_FOUND")
        void throwsWhenGroupNotFound() {
            // given
            when(challengeGroupRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertBusinessException(() -> groupService.joinGroup(999L, 2L), ErrorCode.GROUP_NOT_FOUND);
        }

        @Test
        @DisplayName("초대코드 그룹이면 INVITE_CODE_REQUIRED")
        void throwsWhenGroupIsCodeOnly() {
            // given
            ChallengeGroup group = group(12L, "비공개 모임", GroupCategory.EXERCISE, Visibility.CODE_ONLY, 6);
            when(challengeGroupRepository.findById(12L)).thenReturn(Optional.of(group));

            // when & then
            assertBusinessException(() -> groupService.joinGroup(12L, 2L), ErrorCode.INVITE_CODE_REQUIRED);
            verify(groupMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("이미 참여 이력이 있으면 ALREADY_JOINED")
        void throwsWhenAlreadyJoined() {
            // given
            ChallengeGroup group = group(12L, "오운완 모임", GroupCategory.EXERCISE, Visibility.PUBLIC, 6);
            Challenge challenge = challenge(50L, 12L, ChallengeStatus.READY);
            when(challengeGroupRepository.findById(12L)).thenReturn(Optional.of(group));
            when(challengeRepository.findFirstByGroupIdAndStatus(12L, ChallengeStatus.READY))
                    .thenReturn(Optional.of(challenge));
            when(groupMemberRepository.existsByGroupIdAndUserId(12L, 2L)).thenReturn(true);

            // when & then
            assertBusinessException(() -> groupService.joinGroup(12L, 2L), ErrorCode.ALREADY_JOINED);
            verify(groupMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("정원이 가득 차면 GROUP_FULL")
        void throwsWhenGroupIsFull() {
            // given
            ChallengeGroup group = group(12L, "오운완 모임", GroupCategory.EXERCISE, Visibility.PUBLIC, 2);
            Challenge challenge = challenge(50L, 12L, ChallengeStatus.READY);
            when(challengeGroupRepository.findById(12L)).thenReturn(Optional.of(group));
            when(challengeRepository.findFirstByGroupIdAndStatus(12L, ChallengeStatus.READY))
                    .thenReturn(Optional.of(challenge));
            when(groupMemberRepository.existsByGroupIdAndUserId(12L, 2L)).thenReturn(false);
            when(groupMemberRepository.countByGroupIdAndStatus(12L, GroupMemberStatus.ACTIVE))
                    .thenReturn(2L);

            // when & then
            assertBusinessException(() -> groupService.joinGroup(12L, 2L), ErrorCode.GROUP_FULL);
            verify(groupMemberRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("leaveGroup - 그룹 퇴장")
    class LeaveGroup {

        @Test
        @DisplayName("일반 멤버가 퇴장하면 그룹 멤버와 현재 시즌 멤버를 LEFT 처리한다")
        void leavesGroupAndCurrentChallengeMembers() {
            // given
            ChallengeGroup group = group(12L, "오운완 모임", GroupCategory.EXERCISE, Visibility.PUBLIC, 6);
            Challenge activeChallenge = challenge(50L, 12L, ChallengeStatus.ACTIVE);
            Challenge readyChallenge = challenge(51L, 12L, ChallengeStatus.READY);
            GroupMember groupMember = groupMember(30L, group, 2L);
            ChallengeMember activeMember = challengeMember(70L, activeChallenge, 2L, ChallengeMemberRole.MEMBER);
            ChallengeMember readyMember = challengeMember(71L, readyChallenge, 2L, ChallengeMemberRole.MEMBER);
            when(challengeGroupRepository.findById(12L)).thenReturn(Optional.of(group));
            when(groupMemberRepository.findByGroupIdAndUserId(12L, 2L)).thenReturn(Optional.of(groupMember));
            when(challengeRepository.findFirstByGroupIdAndStatus(12L, ChallengeStatus.ACTIVE))
                    .thenReturn(Optional.of(activeChallenge));
            when(challengeRepository.findFirstByGroupIdAndStatus(12L, ChallengeStatus.READY))
                    .thenReturn(Optional.of(readyChallenge));
            when(challengeMemberRepository.findByChallengeIdAndUserId(50L, 2L)).thenReturn(Optional.of(activeMember));
            when(challengeMemberRepository.findByChallengeIdAndUserId(51L, 2L)).thenReturn(Optional.of(readyMember));

            // when
            groupService.leaveGroup(12L, 2L);

            // then
            assertThat(groupMember.getStatus()).isEqualTo(GroupMemberStatus.LEFT);
            assertThat(activeMember.getStatus()).isEqualTo(ChallengeMemberStatus.LEFT);
            assertThat(readyMember.getStatus()).isEqualTo(ChallengeMemberStatus.LEFT);
        }

        @Test
        @DisplayName("참여 중인 멤버가 아니면 NOT_GROUP_MEMBER")
        void throwsWhenNotActiveGroupMember() {
            // given
            ChallengeGroup group = group(12L, "오운완 모임", GroupCategory.EXERCISE, Visibility.PUBLIC, 6);
            GroupMember groupMember = groupMember(30L, group, 2L);
            groupMember.leave();
            when(challengeGroupRepository.findById(12L)).thenReturn(Optional.of(group));
            when(groupMemberRepository.findByGroupIdAndUserId(12L, 2L)).thenReturn(Optional.of(groupMember));

            // when & then
            assertBusinessException(() -> groupService.leaveGroup(12L, 2L), ErrorCode.NOT_GROUP_MEMBER);
        }

        @Test
        @DisplayName("그룹 OWNER는 바로 퇴장할 수 없다")
        void throwsWhenOwnerLeaves() {
            // given
            ChallengeGroup group = group(12L, "오운완 모임", GroupCategory.EXERCISE, Visibility.PUBLIC, 6);
            GroupMember ownerMember = groupMember(30L, group, 1L);
            when(challengeGroupRepository.findById(12L)).thenReturn(Optional.of(group));
            when(groupMemberRepository.findByGroupIdAndUserId(12L, 1L)).thenReturn(Optional.of(ownerMember));

            // when & then
            assertBusinessException(() -> groupService.leaveGroup(12L, 1L), ErrorCode.GROUP_OWNER_CANNOT_LEAVE);
            assertThat(ownerMember.getStatus()).isEqualTo(GroupMemberStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("getMyGroups - 내 그룹 목록 조회")
    class GetMyGroups {

        @Test
        @DisplayName("참여 중인 시즌을 커서 기반으로 반환한다")
        void returnsMyGroupsWithCursor() {
            // given
            ChallengeGroup group = group(12L, "오운완 모임", GroupCategory.EXERCISE, Visibility.PUBLIC, 6);
            Challenge challenge = challenge(50L, 12L, ChallengeStatus.ACTIVE);
            ChallengeMember member = challengeMember(70L, challenge, 2L, ChallengeMemberRole.MEMBER);
            when(challengeMemberRepository.findAllByUserIdAndStatus(2L, ChallengeMemberStatus.ACTIVE))
                    .thenReturn(List.of(member));
            when(challengeGroupRepository.findAllById(List.of(12L))).thenReturn(List.of(group));
            when(challengeMemberRepository.countByChallengeIdAndStatus(50L, ChallengeMemberStatus.ACTIVE))
                    .thenReturn(3L);
            when(challengeProgressCalculator.calculateCurrentDay(eq(challenge), any(LocalDate.class)))
                    .thenReturn(2);
            when(challengeProgressCalculator.calculatePeriodProgressRate(2, 7)).thenReturn(28.6);

            // when
            var response = groupService.getMyGroups(2L, null, null, 20);

            // then
            assertThat(response.content()).hasSize(1);
            assertThat(response.content().get(0).groupId()).isEqualTo(12L);
            assertThat(response.content().get(0).participantCount()).isEqualTo(3);
            assertThat(response.hasNext()).isFalse();
        }

        @Test
        @DisplayName("그룹 정보를 찾을 수 없으면 GROUP_NOT_FOUND")
        void throwsWhenGroupMissing() {
            // given
            Challenge challenge = challenge(50L, 12L, ChallengeStatus.ACTIVE);
            ChallengeMember member = challengeMember(70L, challenge, 2L, ChallengeMemberRole.MEMBER);
            when(challengeMemberRepository.findAllByUserIdAndStatus(2L, ChallengeMemberStatus.ACTIVE))
                    .thenReturn(List.of(member));
            when(challengeGroupRepository.findAllById(List.of(12L))).thenReturn(List.of());

            // when & then
            assertBusinessException(() -> groupService.getMyGroups(2L, null, null, 20), ErrorCode.GROUP_NOT_FOUND);
        }
    }
}
