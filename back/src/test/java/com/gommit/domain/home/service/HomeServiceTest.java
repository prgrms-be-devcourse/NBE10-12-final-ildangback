package com.gommit.domain.home.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.FrequencyType;
import com.gommit.domain.challenge.repository.ChallengeRepository;
import com.gommit.domain.checkin.repository.CheckInRepository;
import com.gommit.domain.checkin.repository.CheckInRepository.CheckInCountByDate;
import com.gommit.domain.group.entity.ChallengeGroup;
import com.gommit.domain.group.entity.GroupCategory;
import com.gommit.domain.group.entity.GroupStatus;
import com.gommit.domain.group.entity.MapType;
import com.gommit.domain.group.entity.Visibility;
import com.gommit.domain.group.repository.ChallengeGroupRepository;
import com.gommit.domain.group.service.GroupService;
import com.gommit.domain.home.dto.response.ActivityResponse;
import com.gommit.domain.home.dto.response.GrassResponse;
import com.gommit.domain.home.dto.response.HomeResponse;
import com.gommit.domain.item.dto.response.CharacterResponse;
import com.gommit.domain.item.entity.ItemSlot;
import com.gommit.domain.item.service.UserItemService;
import com.gommit.domain.point.dto.response.PointBalanceResponse;
import com.gommit.domain.point.entity.UserPointHistory;
import com.gommit.domain.point.entity.UserPointReason;
import com.gommit.domain.point.service.PersonalPointService;
import com.gommit.domain.user.dto.response.UserProfileResponse;
import com.gommit.domain.user.service.UserService;
import com.gommit.global.dto.SliceResponse;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import com.gommit.global.time.BusinessClock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("HomeService")
class HomeServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private UserItemService userItemService;

    @Mock
    private PersonalPointService pointService;

    @Mock
    private GroupService groupService;

    @Mock
    private ChallengeRepository challengeRepository;

    @Mock
    private ChallengeGroupRepository challengeGroupRepository;

    @Mock
    private CheckInRepository checkInRepository;

    @Mock
    private BusinessClock businessClock;

    @InjectMocks
    private HomeService homeService;

    private static final Long USER_ID = 1L;

    private CharacterResponse emptyCharacter() {
        Map<ItemSlot, String> slots = new EnumMap<>(ItemSlot.class);
        for (ItemSlot slot : ItemSlot.values()) slots.put(slot, null);
        return new CharacterResponse(slots);
    }

    private UserProfileResponse stubProfile() {
        return new UserProfileResponse(USER_ID, "t@t.com", "테스터", null, 0, 0, null, LocalDateTime.now());
    }

    private UserPointHistory history(UserPointReason reason, Long challengeId) {
        return UserPointHistory.of(USER_ID, challengeId, "소스", 100, reason, 100);
    }

    // ──────────────────────────────────────────────────
    // getGrass
    // ──────────────────────────────────────────────────

    @Nested
    @DisplayName("getGrass")
    @MockitoSettings(strictness = Strictness.LENIENT)
    class GetGrass {

        @BeforeEach
        void setUp() {
            given(checkInRepository.countByUserIdGroupByDateBetween(eq(USER_ID), any(), any()))
                    .willReturn(List.of());
        }

        @Test
        @DisplayName("from이 to보다 늦으면 INVALID_INPUT_VALUE 예외가 발생한다")
        void t1() {
            assertThatThrownBy(() -> homeService.getGrass(USER_ID, LocalDate.of(2025, 6, 10), LocalDate.of(2025, 6, 1)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        }

        @Test
        @DisplayName("범위가 366일을 초과하면 INVALID_INPUT_VALUE 예외가 발생한다")
        void t2() {
            assertThatThrownBy(() -> homeService.getGrass(USER_ID, LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 3)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        }

        @Test
        @DisplayName("유효한 범위이면 from~to 날짜별 GrassResponse 목록이 순서대로 반환된다")
        void t3() {
            LocalDate from = LocalDate.of(2025, 6, 1);
            LocalDate to = LocalDate.of(2025, 6, 3);

            SliceResponse<GrassResponse> result = homeService.getGrass(USER_ID, from, to);

            assertThat(result.content()).hasSize(3);
            assertThat(result.content().get(0).date()).isEqualTo(LocalDate.of(2025, 6, 1));
            assertThat(result.content().get(1).date()).isEqualTo(LocalDate.of(2025, 6, 2));
            assertThat(result.content().get(2).date()).isEqualTo(LocalDate.of(2025, 6, 3));
        }

        @Test
        @DisplayName("from == to이면 하루치 결과만 반환된다")
        void t4() {
            LocalDate date = LocalDate.of(2025, 6, 15);

            SliceResponse<GrassResponse> result = homeService.getGrass(USER_ID, date, date);

            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).date()).isEqualTo(date);
        }

        @Test
        @DisplayName("체크인이 없으면 checkInCount=0, level=0이다")
        void t5() {
            LocalDate date = LocalDate.of(2025, 6, 1);

            SliceResponse<GrassResponse> result = homeService.getGrass(USER_ID, date, date);

            assertThat(result.content().get(0).checkInCount()).isEqualTo(0);
            assertThat(result.content().get(0).level()).isEqualTo(0);
        }

        @Test
        @DisplayName("체크인이 2건이면 checkInCount=2, level=2이다")
        void t5GrassCheckIn2() {
            LocalDate date = LocalDate.of(2025, 6, 1);

            given(checkInRepository.countByUserIdGroupByDateBetween(USER_ID, date, date))
                    .willReturn(List.of(stubRow(date, 2L)));

            SliceResponse<GrassResponse> result = homeService.getGrass(USER_ID, date, date);

            assertThat(result.content().get(0).checkInCount()).isEqualTo(2);
            assertThat(result.content().get(0).level()).isEqualTo(2);
        }

        @Test
        @DisplayName("체크인이 5건 이상이면 level은 최대 4이다")
        void t5GrassCheckIn5() {
            LocalDate date = LocalDate.of(2025, 6, 1);

            given(checkInRepository.countByUserIdGroupByDateBetween(USER_ID, date, date))
                    .willReturn(List.of(stubRow(date, 5L)));

            SliceResponse<GrassResponse> result = homeService.getGrass(USER_ID, date, date);

            assertThat(result.content().get(0).checkInCount()).isEqualTo(5);
            assertThat(result.content().get(0).level()).isEqualTo(4);
        }

        private CheckInCountByDate stubRow(LocalDate date, long count) {
            return new CheckInCountByDate() {
                @Override
                public LocalDate getBusinessDate() {
                    return date;
                }

                @Override
                public Long getCount() {
                    return count;
                }
            };
        }
    }

    // ──────────────────────────────────────────────────
    // getActivities — commit prefix 변환
    // ──────────────────────────────────────────────────

    @Nested
    @DisplayName("getActivities - commit prefix")
    class GetActivities {

        @Test
        @DisplayName("이력이 없으면 빈 content가 반환된다")
        void t6() {
            given(pointService.getRecentHistories(USER_ID, 3)).willReturn(List.of());

            SliceResponse<ActivityResponse> result = homeService.getActivities(USER_ID);

            assertThat(result.content()).isEmpty();
        }

        @Test
        @DisplayName("CHECK_IN + challengeId 없으면 feat: 접두사를 가진다")
        void t7() {
            given(pointService.getRecentHistories(USER_ID, 3))
                    .willReturn(List.of(history(UserPointReason.CHECK_IN, null)));

            SliceResponse<ActivityResponse> result = homeService.getActivities(USER_ID);

            assertThat(result.content().get(0).commitPrefix()).isEqualTo("feat:");
        }

        @Test
        @DisplayName("ITEM_PURCHASE는 chore: 접두사를 가진다")
        void t8() {
            given(pointService.getRecentHistories(USER_ID, 3))
                    .willReturn(List.of(history(UserPointReason.ITEM_PURCHASE, null)));

            SliceResponse<ActivityResponse> result = homeService.getActivities(USER_ID);

            assertThat(result.content().get(0).commitPrefix()).isEqualTo("chore:");
        }

        @Test
        @DisplayName("CHALLENGE_BONUS는 feat: 접두사를 가진다")
        void t9() {
            given(pointService.getRecentHistories(USER_ID, 3))
                    .willReturn(List.of(history(UserPointReason.CHALLENGE_BONUS, null)));

            SliceResponse<ActivityResponse> result = homeService.getActivities(USER_ID);

            assertThat(result.content().get(0).commitPrefix()).isEqualTo("feat:");
        }

        @Test
        @DisplayName("WITHDRAWAL_PENALTY는 fix: 접두사를 가진다")
        void t10() {
            given(pointService.getRecentHistories(USER_ID, 3))
                    .willReturn(List.of(history(UserPointReason.WITHDRAWAL_PENALTY, null)));

            SliceResponse<ActivityResponse> result = homeService.getActivities(USER_ID);

            assertThat(result.content().get(0).commitPrefix()).isEqualTo("fix:");
        }

        @Test
        @DisplayName("MONTHLY_MERGE_BONUS는 feat: 접두사를 가진다")
        void t11() {
            given(pointService.getRecentHistories(USER_ID, 3))
                    .willReturn(List.of(history(UserPointReason.MONTHLY_MERGE_BONUS, null)));

            SliceResponse<ActivityResponse> result = homeService.getActivities(USER_ID);

            assertThat(result.content().get(0).commitPrefix()).isEqualTo("feat:");
        }

        @Test
        @DisplayName("CHECK_IN + EXERCISE 카테고리이면 workout: 접두사를 가진다")
        void t12() {
            Long challengeId = 100L;
            Long groupId = 200L;

            given(pointService.getRecentHistories(USER_ID, 3))
                    .willReturn(List.of(history(UserPointReason.CHECK_IN, challengeId)));

            Challenge challenge = Challenge.builder()
                    .groupId(groupId)
                    .seqNo(1)
                    .startDate(LocalDate.now())
                    .endDate(LocalDate.now().plusDays(30))
                    .frequencyType(FrequencyType.DAILY)
                    .dailyCheckInCount(1)
                    .requiredDayCount(30)
                    .allowPhoto(false)
                    .build();
            ReflectionTestUtils.setField(challenge, "id", challengeId);

            ChallengeGroup group = ChallengeGroup.builder()
                    .name("운동 그룹")
                    .category(GroupCategory.EXERCISE)
                    .mapType(MapType.GYM)
                    .visibility(Visibility.PUBLIC)
                    .maxMembers(10)
                    .ownerId(USER_ID)
                    .build();
            ReflectionTestUtils.setField(group, "id", groupId);

            given(challengeRepository.findAllById(List.of(challengeId))).willReturn(List.of(challenge));
            given(challengeGroupRepository.findAllById(any())).willReturn(List.of(group));

            SliceResponse<ActivityResponse> result = homeService.getActivities(USER_ID);

            assertThat(result.content().get(0).commitPrefix()).isEqualTo("workout:");
        }

        @Test
        @DisplayName("CHECK_IN + READING 카테고리이면 docs: 접두사를 가진다")
        void t13() {
            Long challengeId = 101L;
            Long groupId = 201L;

            given(pointService.getRecentHistories(USER_ID, 3))
                    .willReturn(List.of(history(UserPointReason.CHECK_IN, challengeId)));

            Challenge challenge = Challenge.builder()
                    .groupId(groupId)
                    .seqNo(1)
                    .startDate(LocalDate.now())
                    .endDate(LocalDate.now().plusDays(30))
                    .frequencyType(FrequencyType.DAILY)
                    .dailyCheckInCount(1)
                    .requiredDayCount(30)
                    .allowPhoto(false)
                    .build();
            ReflectionTestUtils.setField(challenge, "id", challengeId);

            ChallengeGroup group = ChallengeGroup.builder()
                    .name("독서 그룹")
                    .category(GroupCategory.READING)
                    .mapType(MapType.STUDY_ROOM)
                    .visibility(Visibility.PUBLIC)
                    .maxMembers(10)
                    .ownerId(USER_ID)
                    .build();
            ReflectionTestUtils.setField(group, "id", groupId);

            given(challengeRepository.findAllById(List.of(challengeId))).willReturn(List.of(challenge));
            given(challengeGroupRepository.findAllById(any())).willReturn(List.of(group));

            SliceResponse<ActivityResponse> result = homeService.getActivities(USER_ID);

            assertThat(result.content().get(0).commitPrefix()).isEqualTo("docs:");
        }

        @Test
        @DisplayName("CHECK_IN + ETC 카테고리이면 chore: 접두사를 가진다")
        void t14() {
            Long challengeId = 102L;
            Long groupId = 202L;

            given(pointService.getRecentHistories(USER_ID, 3))
                    .willReturn(List.of(history(UserPointReason.CHECK_IN, challengeId)));

            Challenge challenge = Challenge.builder()
                    .groupId(groupId)
                    .seqNo(1)
                    .startDate(LocalDate.now())
                    .endDate(LocalDate.now().plusDays(30))
                    .frequencyType(FrequencyType.DAILY)
                    .dailyCheckInCount(1)
                    .requiredDayCount(30)
                    .allowPhoto(false)
                    .build();
            ReflectionTestUtils.setField(challenge, "id", challengeId);

            ChallengeGroup group = ChallengeGroup.builder()
                    .name("기타 그룹")
                    .category(GroupCategory.ETC)
                    .mapType(MapType.STUDY_ROOM)
                    .visibility(Visibility.PUBLIC)
                    .maxMembers(10)
                    .ownerId(USER_ID)
                    .build();
            ReflectionTestUtils.setField(group, "id", groupId);

            given(challengeRepository.findAllById(List.of(challengeId))).willReturn(List.of(challenge));
            given(challengeGroupRepository.findAllById(any())).willReturn(List.of(group));

            SliceResponse<ActivityResponse> result = homeService.getActivities(USER_ID);

            assertThat(result.content().get(0).commitPrefix()).isEqualTo("chore:");
        }
    }

    // ──────────────────────────────────────────────────
    // getHome
    // ──────────────────────────────────────────────────

    @Nested
    @DisplayName("getHome")
    class GetHome {

        @Test
        @DisplayName("정상 호출 시 유저 정보와 포인트 잔액이 담긴 HomeResponse를 반환한다")
        void t15() {
            given(userService.getMyProfile(USER_ID)).willReturn(stubProfile());
            given(userItemService.getMyCharacter(USER_ID)).willReturn(emptyCharacter());
            given(groupService.getMyGroups(eq(USER_ID), eq(GroupStatus.ACTIVE), any(), eq(100)))
                    .willReturn(new SliceResponse<>(List.of(), false, null));
            given(pointService.getMyBalance(USER_ID)).willReturn(new PointBalanceResponse(500, 0, 0, 0));
            given(checkInRepository.countMine(eq(USER_ID), isNull(), isNull(), any(), any()))
                    .willReturn(5L);
            given(checkInRepository.countDistinctDatesByUserIdBetween(eq(USER_ID), any(), any()))
                    .willReturn(3L);
            given(businessClock.today()).willReturn(LocalDate.of(2025, 6, 15));
            given(businessClock.firstDayOfBusinessMonth()).willReturn(LocalDate.of(2025, 6, 1));

            HomeResponse result = homeService.getHome(USER_ID);

            assertThat(result.nickname()).isEqualTo("테스터");
            assertThat(result.pointBalance()).isEqualTo(500);
            assertThat(result.character()).isNotNull();
        }

        @Test
        @DisplayName("오늘 완료한 챌린지 수와 전체 챌린지 수를 정확히 집계한다")
        void t16() {
            given(userService.getMyProfile(USER_ID)).willReturn(stubProfile());
            given(userItemService.getMyCharacter(USER_ID)).willReturn(emptyCharacter());
            given(pointService.getMyBalance(USER_ID)).willReturn(new PointBalanceResponse(0, 0, 0, 0));
            given(businessClock.today()).willReturn(LocalDate.of(2025, 6, 15));
            given(businessClock.firstDayOfBusinessMonth()).willReturn(LocalDate.of(2025, 6, 1));

            // todayCompleted=true 1개, false 2개
            var completed = myGroup(1L, 1L, true);
            var notCompleted1 = myGroup(2L, 2L, false);
            var notCompleted2 = myGroup(3L, 3L, false);
            given(groupService.getMyGroups(eq(USER_ID), eq(GroupStatus.ACTIVE), any(), eq(100)))
                    .willReturn(new SliceResponse<>(List.of(completed, notCompleted1, notCompleted2), false, null));

            HomeResponse result = homeService.getHome(USER_ID);

            assertThat(result.todayTotalCount()).isEqualTo(3);
            assertThat(result.todayCompletedCount()).isEqualTo(1);
        }

        private com.gommit.domain.group.dto.response.MyGroupSummaryResponse myGroup(
                Long groupId, Long challengeId, boolean todayCompleted) {
            return new com.gommit.domain.group.dto.response.MyGroupSummaryResponse(
                    groupId,
                    challengeId,
                    "그룹",
                    GroupCategory.STUDY,
                    GroupStatus.ACTIVE,
                    com.gommit.domain.challenge.entity.ChallengeStatus.ACTIVE,
                    5,
                    1,
                    30,
                    0.03,
                    1,
                    1,
                    todayCompleted);
        }
    }
}
