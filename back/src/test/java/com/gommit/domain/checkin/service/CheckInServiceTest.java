package com.gommit.domain.checkin.service;

import static com.gommit.domain.checkin.CheckInFixture.START;
import static com.gommit.domain.checkin.CheckInFixture.dailyChallenge;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.service.ChallengeProgressCalculator;
import com.gommit.domain.challenge.service.ChallengeStreakService;
import com.gommit.domain.challenge.service.MemberCheckInResult;
import com.gommit.domain.checkin.dto.request.SubmitCheckInRequest;
import com.gommit.domain.checkin.dto.response.CheckInCursorResponse;
import com.gommit.domain.checkin.dto.response.CheckInResponse;
import com.gommit.domain.checkin.dto.response.CheckInResultResponse;
import com.gommit.domain.checkin.dto.response.MyCheckInCursorResponse;
import com.gommit.domain.checkin.dto.response.RecentCheckInResponse;
import com.gommit.domain.checkin.dto.response.TodayCheckInStatusResponse;
import com.gommit.domain.checkin.entity.CheckIn;
import com.gommit.domain.checkin.entity.CheckInType;
import com.gommit.domain.checkin.entity.MediaType;
import com.gommit.domain.checkin.media.CheckInMediaStore;
import com.gommit.domain.checkin.policy.CheckInPolicy;
import com.gommit.domain.checkin.repository.CheckInRepository;
import com.gommit.domain.checkin.support.CheckInPreconditions;
import com.gommit.domain.checkin.support.CheckInPreconditions.ReadDateAccess;
import com.gommit.domain.group.repository.ChallengeGroupRepository;
import com.gommit.domain.point.entity.UserPointReason;
import com.gommit.domain.point.service.PersonalPointService;
import com.gommit.domain.user.service.UserService;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import com.gommit.global.time.BusinessClock;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
@DisplayName("CheckInService")
class CheckInServiceTest {

    private static final long CHALLENGE_ID = 1L;
    private static final long USER_ID = 42L;
    // businessDate 가 챌린지 기간(2026-09-01~) 안이 되도록 고정.
    private static final LocalDate TODAY = START.plusDays(10);

    @Mock
    private CheckInRepository checkInRepository;

    @Mock
    private CheckInPreconditions preconditions;

    @Mock
    private CheckInPolicy policy;

    @Mock
    private ChallengeProgressCalculator progressCalculator;

    @Mock
    private CheckInMediaStore mediaStore;

    @Mock
    private PersonalPointService personalPointService;

    @Mock
    private UserService userService;

    @Mock
    private ChallengeStreakService challengeStreakService;

    @Mock
    private ChallengeGroupRepository challengeGroupRepository;

    private CheckInService service;

    @BeforeEach
    void setUp() {
        // businessDate 04:00 컷오프에 걸리지 않도록 이후 시각으로 고정
        Clock clock = Clock.fixed(TODAY.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneId.of("UTC"));
        service = new CheckInService(
                checkInRepository,
                preconditions,
                policy,
                progressCalculator,
                mediaStore,
                personalPointService,
                userService,
                challengeStreakService,
                challengeGroupRepository,
                new BusinessClock(clock));
        lenient().when(userService.findNicknames(anyList())).thenReturn(Map.of(USER_ID, "인증러"));
        lenient().when(challengeGroupRepository.findNameById(1L)).thenReturn(Optional.of("오운완 모임"));
    }

    private MultipartFile media() {
        return new MockMultipartFile("media", "shot.png", "image/png", new byte[] {1, 2, 3});
    }

    private SubmitCheckInRequest request(String memo) {
        return new SubmitCheckInRequest(CheckInType.PHOTO, memo);
    }

    private void givenActiveMemberAndValidDay(Challenge challenge) {
        when(preconditions.getActiveChallengeForActiveMember(CHALLENGE_ID, USER_ID))
                .thenReturn(challenge);
    }

    private CheckIn checkInRow(Long id) {
        CheckIn checkIn = new CheckIn(CHALLENGE_ID, USER_ID, 1, CheckInType.PHOTO, "key", MediaType.IMAGE, null, TODAY);
        ReflectionTestUtils.setField(checkIn, "id", id);
        return checkIn;
    }

    @Nested
    @DisplayName("submit")
    class Submit {

        @Test
        @DisplayName("성공 — roundNo 는 그날 기존 인증 수 + 1, 포인트 적립")
        void succeeds() {
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 3);
            givenActiveMemberAndValidDay(challenge);
            when(checkInRepository.countByChallengeIdAndUserIdAndBusinessDate(CHALLENGE_ID, USER_ID, TODAY))
                    .thenReturn(1);
            when(mediaStore.store(any())).thenReturn("check-ins/2026/09/uuid.png");
            when(policy.checkInReward()).thenReturn(10);
            when(checkInRepository.saveAndFlush(any(CheckIn.class))).thenAnswer(inv -> {
                CheckIn c = inv.getArgument(0);
                ReflectionTestUtils.setField(c, "id", 100L);
                return c;
            });

            CheckInResultResponse result = service.submit(USER_ID, CHALLENGE_ID, request(null), media());

            assertThat(result.currentCount()).isEqualTo(2);
            assertThat(result.targetCount()).isEqualTo(3);
            assertThat(result.dailyCompleted()).isFalse();
            assertThat(result.earnedUserPoints()).isEqualTo(10);
            assertThat(result.checkIn().roundNo()).isEqualTo(2);
            assertThat(result.checkIn().mediaType()).isEqualTo(MediaType.IMAGE);

            // 순서: 미디어 저장 → row 저장(flush) → 포인트 적립.
            var order = inOrder(mediaStore, checkInRepository, personalPointService);
            order.verify(mediaStore).store(any());
            order.verify(checkInRepository).saveAndFlush(any(CheckIn.class));
            order.verify(personalPointService).reward(USER_ID, CHALLENGE_ID, 10, UserPointReason.CHECK_IN, "오운완 모임");
        }

        @Test
        @DisplayName("목표 회차를 채우면 dailyCompleted=true")
        void completesDaily() {
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 1);
            givenActiveMemberAndValidDay(challenge);
            when(checkInRepository.countByChallengeIdAndUserIdAndBusinessDate(CHALLENGE_ID, USER_ID, TODAY))
                    .thenReturn(0);
            when(mediaStore.store(any())).thenReturn("check-ins/2026/09/uuid.png");
            when(checkInRepository.saveAndFlush(any(CheckIn.class))).thenAnswer(inv -> inv.getArgument(0));
            when(challengeStreakService.onMemberDailyComplete(CHALLENGE_ID, USER_ID, TODAY))
                    .thenReturn(new MemberCheckInResult(3, 2, 4, false));

            CheckInResultResponse result = service.submit(USER_ID, CHALLENGE_ID, request(null), media());

            assertThat(result.dailyCompleted()).isTrue();
            assertThat(result.currentStreak()).isEqualTo(3);
            assertThat(result.groupCompletedCount()).isEqualTo(2);
            assertThat(result.groupTotalCount()).isEqualTo(4);
        }

        @Test
        @DisplayName("그룹명을 찾을 수 없으면 포인트 이력 sourceName 은 고정 라벨로 폴백한다")
        void fallsBackToLabelWhenGroupNameMissing() {
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 3);
            givenActiveMemberAndValidDay(challenge);
            when(checkInRepository.countByChallengeIdAndUserIdAndBusinessDate(CHALLENGE_ID, USER_ID, TODAY))
                    .thenReturn(1);
            when(mediaStore.store(any())).thenReturn("check-ins/2026/09/uuid.png");
            when(policy.checkInReward()).thenReturn(10);
            when(checkInRepository.saveAndFlush(any(CheckIn.class))).thenAnswer(inv -> inv.getArgument(0));
            when(challengeGroupRepository.findNameById(1L)).thenReturn(Optional.empty());

            service.submit(USER_ID, CHALLENGE_ID, request(null), media());

            verify(personalPointService).reward(USER_ID, CHALLENGE_ID, 10, UserPointReason.CHECK_IN, "인증");
        }

        // 챌린지 ACTIVE 여부 / 멤버 ACTIVE 여부 판정 자체는 CheckInPreconditionsTest 가 검증한다.
        // 여기서는 preconditions 가 던진 예외를 service 가 그대로 전파하는지만 본다.
        @Test
        @DisplayName("진행 중이 아닌 챌린지면 CHALLENGE_NOT_ACTIVE")
        void rejectsInactiveChallenge() {
            when(preconditions.getActiveChallengeForActiveMember(CHALLENGE_ID, USER_ID))
                    .thenThrow(new BusinessException(ErrorCode.CHALLENGE_NOT_ACTIVE));

            assertBusiness(
                    () -> service.submit(USER_ID, CHALLENGE_ID, request(null), media()),
                    ErrorCode.CHALLENGE_NOT_ACTIVE);
        }

        @Test
        @DisplayName("참여자가 아니거나 이탈했으면 CHALLENGE_NOT_MEMBER")
        void rejectsNonMember() {
            when(preconditions.getActiveChallengeForActiveMember(CHALLENGE_ID, USER_ID))
                    .thenThrow(new BusinessException(ErrorCode.CHALLENGE_NOT_MEMBER));

            assertBusiness(
                    () -> service.submit(USER_ID, CHALLENGE_ID, request(null), media()),
                    ErrorCode.CHALLENGE_NOT_MEMBER);
        }

        @Test
        @DisplayName("인증 대상일이 아니면 NOT_CHECK_IN_DAY")
        void rejectsNonCheckInDay() {
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 1);
            givenActiveMemberAndValidDay(challenge);
            doThrow(new BusinessException(ErrorCode.NOT_CHECK_IN_DAY))
                    .when(policy)
                    .validateCheckInDay(challenge, TODAY);

            assertBusiness(
                    () -> service.submit(USER_ID, CHALLENGE_ID, request(null), media()), ErrorCode.NOT_CHECK_IN_DAY);
        }

        @Test
        @DisplayName("허용되지 않은 인증 방식이면 CHECK_IN_TYPE_NOT_ALLOWED")
        void rejectsDisallowedType() {
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 1);
            givenActiveMemberAndValidDay(challenge);
            doThrow(new BusinessException(ErrorCode.CHECK_IN_TYPE_NOT_ALLOWED))
                    .when(policy)
                    .validateAllowedType(challenge, CheckInType.PHOTO);

            assertBusiness(
                    () -> service.submit(USER_ID, CHALLENGE_ID, request(null), media()),
                    ErrorCode.CHECK_IN_TYPE_NOT_ALLOWED);
        }

        @Test
        @DisplayName("이미 목표 회차를 채웠으면 DAILY_LIMIT_EXCEEDED")
        void rejectsWhenLimitReached() {
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 2);
            givenActiveMemberAndValidDay(challenge);
            when(checkInRepository.countByChallengeIdAndUserIdAndBusinessDate(CHALLENGE_ID, USER_ID, TODAY))
                    .thenReturn(2);

            assertBusiness(
                    () -> service.submit(USER_ID, CHALLENGE_ID, request(null), media()),
                    ErrorCode.DAILY_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("uk_check_ins 위반은 DAILY_LIMIT_EXCEEDED 로 변환한다")
        void translatesUniqueViolation() {
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 3);
            givenActiveMemberAndValidDay(challenge);
            when(checkInRepository.countByChallengeIdAndUserIdAndBusinessDate(CHALLENGE_ID, USER_ID, TODAY))
                    .thenReturn(0);
            when(mediaStore.store(any())).thenReturn("check-ins/2026/09/uuid.png");
            when(checkInRepository.saveAndFlush(any(CheckIn.class)))
                    .thenThrow(new DataIntegrityViolationException("uk_check_ins"));

            assertBusiness(
                    () -> service.submit(USER_ID, CHALLENGE_ID, request(null), media()),
                    ErrorCode.DAILY_LIMIT_EXCEEDED);

            // insert 가 실패하면 방금 쓴 파일을 정리한다 — orphan 방지.
            verify(mediaStore).delete(anyString());
            verify(personalPointService, never()).reward(anyLong(), anyLong(), anyInt(), any(), any());
        }

        @Test
        @DisplayName("포인트 적립이 실패하면 방금 올린 미디어를 정리하고 예외를 되던진다")
        void cleansMediaWhenRewardFails() {
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 3);
            givenActiveMemberAndValidDay(challenge);
            when(checkInRepository.countByChallengeIdAndUserIdAndBusinessDate(CHALLENGE_ID, USER_ID, TODAY))
                    .thenReturn(1);
            when(mediaStore.store(any())).thenReturn("check-ins/2026/09/uuid.png");
            when(policy.checkInReward()).thenReturn(10);
            when(checkInRepository.saveAndFlush(any(CheckIn.class))).thenAnswer(inv -> inv.getArgument(0));
            doThrow(new IllegalStateException("적립 실패"))
                    .when(personalPointService)
                    .reward(anyLong(), anyLong(), anyInt(), any(), any());

            assertThatThrownBy(() -> service.submit(USER_ID, CHALLENGE_ID, request(null), media()))
                    .isInstanceOf(IllegalStateException.class);

            verify(mediaStore).delete("check-ins/2026/09/uuid.png");
        }
    }

    @Nested
    @DisplayName("getTodayStatus")
    class TodayStatus {

        @Test
        @DisplayName("현재/목표 회차와 대상일 여부, 허용 방식을 준다")
        void returnsStatus() {
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 3);
            when(preconditions.getActiveChallengeForActiveMember(CHALLENGE_ID, USER_ID))
                    .thenReturn(challenge);
            when(checkInRepository.countByChallengeIdAndUserIdAndBusinessDate(CHALLENGE_ID, USER_ID, TODAY))
                    .thenReturn(2);
            when(progressCalculator.canCheckInOn(challenge, TODAY)).thenReturn(true);
            when(policy.allowedTypes(challenge)).thenReturn(List.of(CheckInType.PHOTO));

            TodayCheckInStatusResponse status = service.getTodayStatus(USER_ID, CHALLENGE_ID);

            assertThat(status.businessDate()).isEqualTo(TODAY);
            assertThat(status.currentCount()).isEqualTo(2);
            assertThat(status.targetCount()).isEqualTo(3);
            assertThat(status.completed()).isFalse();
            assertThat(status.isCheckInDay()).isTrue();
            assertThat(status.allowedTypes()).containsExactly(CheckInType.PHOTO);
        }

        @Test
        @DisplayName("ACTIVE 시즌·ACTIVE 멤버가 아니면 preconditions 예외를 그대로 전파한다")
        void propagatesPreconditionFailure() {
            when(preconditions.getActiveChallengeForActiveMember(CHALLENGE_ID, USER_ID))
                    .thenThrow(new BusinessException(ErrorCode.CHALLENGE_NOT_ACTIVE));

            assertBusiness(() -> service.getTodayStatus(USER_ID, CHALLENGE_ID), ErrorCode.CHALLENGE_NOT_ACTIVE);
        }
    }

    @Nested
    @DisplayName("조회 — 접근 제어")
    class Read {

        private final Challenge challenge = dailyChallenge(CHALLENGE_ID, 1);

        @Test
        @DisplayName("갤러리 — ACTIVE 멤버는 maxDate 제한 없이(null) 조회한다")
        void galleryActiveNoDateLimit() {
            when(preconditions.resolveReadDateAccess(CHALLENGE_ID, USER_ID))
                    .thenReturn(new ReadDateAccess(challenge, null));
            when(checkInRepository.findGallery(
                            eq(CHALLENGE_ID), any(), any(), any(), any(), eq(null), any(), any(Pageable.class)))
                    .thenReturn(List.of());

            service.getGallery(USER_ID, CHALLENGE_ID, null, null, null, null, 20);
        }

        @Test
        @DisplayName("갤러리 — 이탈 멤버는 leftAt 이하 날짜로만 조회한다")
        void galleryLeftMemberDateCapped() {
            LocalDate leftOn = TODAY.minusDays(3);
            when(preconditions.resolveReadDateAccess(CHALLENGE_ID, USER_ID))
                    .thenReturn(new ReadDateAccess(challenge, leftOn));
            when(checkInRepository.findGallery(
                            eq(CHALLENGE_ID), any(), any(), any(), any(), eq(leftOn), any(), any(Pageable.class)))
                    .thenReturn(List.of());

            service.getGallery(USER_ID, CHALLENGE_ID, null, null, null, null, 20);
        }

        @Test
        @DisplayName("갤러리 — month 는 그 달의 첫날~마지막날 범위로 조회한다")
        void galleryMonthFilter() {
            when(preconditions.resolveReadDateAccess(CHALLENGE_ID, USER_ID))
                    .thenReturn(new ReadDateAccess(challenge, null));
            when(checkInRepository.findGallery(
                            eq(CHALLENGE_ID),
                            eq(LocalDate.of(2026, 9, 1)),
                            eq(LocalDate.of(2026, 9, 30)),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(Pageable.class)))
                    .thenReturn(List.of());

            service.getGallery(USER_ID, CHALLENGE_ID, YearMonth.of(2026, 9), null, null, null, 20);
        }

        @Test
        @DisplayName("갤러리 — row가 size보다 많으면 초과분을 잘라내고 hasNext=true, nextCursor는 잘라낸 마지막 row의 id")
        void galleryHasNextWhenMoreRowsThanSize() {
            when(preconditions.resolveReadDateAccess(CHALLENGE_ID, USER_ID))
                    .thenReturn(new ReadDateAccess(challenge, null));
            when(checkInRepository.findGallery(
                            eq(CHALLENGE_ID), any(), any(), any(), any(), eq(null), any(), any(Pageable.class)))
                    .thenReturn(List.of(checkInRow(3L), checkInRow(2L), checkInRow(1L))); // size(2)+1

            CheckInCursorResponse response = service.getGallery(USER_ID, CHALLENGE_ID, null, null, null, null, 2);

            assertThat(response.content()).hasSize(2);
            assertThat(response.meta().hasNext()).isTrue();
            assertThat(response.meta().nextCursor()).isEqualTo(2L);
        }

        @Test
        @DisplayName("단건 — 없는 id 는 CHECK_IN_NOT_FOUND")
        void oneNotFound() {
            when(checkInRepository.findById(5L)).thenReturn(Optional.empty());

            assertBusiness(() -> service.getCheckIn(USER_ID, CHALLENGE_ID, 5L), ErrorCode.CHECK_IN_NOT_FOUND);
        }

        @Test
        @DisplayName("단건 — 다른 챌린지의 인증이면 CHALLENGE_NOT_MEMBER")
        void oneWrongChallenge() {
            CheckIn checkIn = checkIn(5L, 999L, TODAY);
            when(checkInRepository.findById(5L)).thenReturn(Optional.of(checkIn));

            assertBusiness(() -> service.getCheckIn(USER_ID, CHALLENGE_ID, 5L), ErrorCode.CHALLENGE_NOT_MEMBER);
        }

        @Test
        @DisplayName("단건 — 이탈일 이후 날짜의 기록이면 CHALLENGE_NOT_MEMBER")
        void oneAfterLeftDate() {
            CheckIn checkIn = checkIn(5L, CHALLENGE_ID, TODAY);
            when(checkInRepository.findById(5L)).thenReturn(Optional.of(checkIn));
            when(preconditions.resolveReadDateAccess(CHALLENGE_ID, USER_ID))
                    .thenReturn(new ReadDateAccess(challenge, TODAY.minusDays(1)));

            assertBusiness(() -> service.getCheckIn(USER_ID, CHALLENGE_ID, 5L), ErrorCode.CHALLENGE_NOT_MEMBER);
        }

        @Test
        @DisplayName("단건 — 참여 기간 내 기록은 조회된다")
        void oneWithinTenure() {
            CheckIn checkIn = checkIn(5L, CHALLENGE_ID, TODAY.minusDays(5));
            when(checkInRepository.findById(5L)).thenReturn(Optional.of(checkIn));
            when(preconditions.resolveReadDateAccess(CHALLENGE_ID, USER_ID))
                    .thenReturn(new ReadDateAccess(challenge, TODAY.minusDays(1)));

            CheckInResponse response = service.getCheckIn(USER_ID, CHALLENGE_ID, 5L);

            assertThat(response.id()).isEqualTo(5L);
        }

        @Test
        @DisplayName("최근 로그 — 메모가 있으면 '{이름}_{메모}', 없으면 '{이름}_{그룹명}'")
        void recentText() {
            CheckIn withMemo = checkIn(5L, CHALLENGE_ID, TODAY);
            ReflectionTestUtils.setField(withMemo, "memo", "오늘 5km 뛰었다");
            CheckIn noMemo = checkIn(6L, CHALLENGE_ID, TODAY);
            when(preconditions.resolveReadDateAccess(CHALLENGE_ID, USER_ID))
                    .thenReturn(new ReadDateAccess(challenge, null));
            when(checkInRepository.findRecent(eq(CHALLENGE_ID), eq(null), any(Limit.class)))
                    .thenReturn(List.of(withMemo, noMemo));

            RecentCheckInResponse recent = service.getRecent(USER_ID, CHALLENGE_ID, 3);

            assertThat(recent.items()).hasSize(2);
            assertThat(recent.items().get(0).text()).isEqualTo("인증러_오늘 5km 뛰었다");
            assertThat(recent.items().get(1).text()).isEqualTo("인증러_오운완 모임");
            assertThat(recent.items().get(0).earnedUserPoints()).isNull();
        }

        @Test
        @DisplayName("미디어 서빙 — 접근 가능한 인증의 파일을 로드한다")
        void loadMedia() {
            CheckIn checkIn = checkIn(5L, CHALLENGE_ID, TODAY.minusDays(2));
            ReflectionTestUtils.setField(checkIn, "mediaKey", "check-ins/2026/09/uuid.png");
            when(checkInRepository.findById(5L)).thenReturn(Optional.of(checkIn));
            when(preconditions.resolveReadDateAccess(CHALLENGE_ID, USER_ID))
                    .thenReturn(new ReadDateAccess(challenge, null));
            Resource resource = new ByteArrayResource(new byte[] {9});
            when(mediaStore.load("check-ins/2026/09/uuid.png")).thenReturn(resource);

            assertThat(service.loadCheckInMedia(USER_ID, 5L)).isSameAs(resource);
        }

        @Test
        @DisplayName("미디어 서빙 — 이탈일 이후 기록은 CHALLENGE_NOT_MEMBER")
        void loadMediaAfterLeft() {
            CheckIn checkIn = checkIn(5L, CHALLENGE_ID, TODAY);
            when(checkInRepository.findById(5L)).thenReturn(Optional.of(checkIn));
            when(preconditions.resolveReadDateAccess(CHALLENGE_ID, USER_ID))
                    .thenReturn(new ReadDateAccess(challenge, TODAY.minusDays(1)));

            assertBusiness(() -> service.loadCheckInMedia(USER_ID, 5L), ErrorCode.CHALLENGE_NOT_MEMBER);
        }
    }

    @Nested
    @DisplayName("getMyCheckIns")
    class MyCheckIns {

        @Test
        @DisplayName("challengeId 지정 시 존재하지 않으면 CHALLENGE_NOT_FOUND")
        void unknownChallenge() {
            when(preconditions.getChallenge(CHALLENGE_ID))
                    .thenThrow(new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));

            assertBusiness(
                    () -> service.getMyCheckIns(USER_ID, CHALLENGE_ID, null, null, null, 20),
                    ErrorCode.CHALLENGE_NOT_FOUND);
        }

        @Test
        @DisplayName("커서 페이지와 totalCount 를 준다")
        void listsWithTotal() {
            when(checkInRepository.findMine(eq(USER_ID), eq(null), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(List.of(checkIn(5L, CHALLENGE_ID, TODAY)));
            when(checkInRepository.countMine(eq(USER_ID), eq(null), any(), any(), any()))
                    .thenReturn(7L);

            MyCheckInCursorResponse response = service.getMyCheckIns(USER_ID, null, null, null, null, 20);

            assertThat(response.content()).hasSize(1);
            assertThat(response.content().get(0).challengeId()).isEqualTo(CHALLENGE_ID);
            assertThat(response.meta().totalCount()).isEqualTo(7L);
        }

        @Test
        @DisplayName("month 를 주면 그 달의 첫날~마지막날로 조회한다")
        void filtersByMonth() {
            YearMonth month = YearMonth.of(2026, 9);
            when(checkInRepository.findMine(
                            eq(USER_ID),
                            eq(null),
                            any(),
                            eq(LocalDate.of(2026, 9, 1)),
                            eq(LocalDate.of(2026, 9, 30)),
                            any(),
                            any(Pageable.class)))
                    .thenReturn(List.of());
            when(checkInRepository.countMine(
                            eq(USER_ID), eq(null), any(), eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 30))))
                    .thenReturn(0L);

            assertThat(service.getMyCheckIns(USER_ID, null, null, month, null, 20)
                            .content())
                    .isEmpty();
        }
    }

    // ===== helpers =====

    private static CheckIn checkIn(long id, long challengeId, LocalDate businessDate) {
        CheckIn checkIn = new CheckIn(
                challengeId, USER_ID, 1, CheckInType.PHOTO, "check-ins/k.png", MediaType.IMAGE, null, businessDate);
        ReflectionTestUtils.setField(checkIn, "id", id);
        return checkIn;
    }

    private void assertBusiness(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }
}
