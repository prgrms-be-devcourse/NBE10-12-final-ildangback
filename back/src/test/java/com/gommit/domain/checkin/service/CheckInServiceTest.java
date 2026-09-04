package com.gommit.domain.checkin.service;

import static com.gommit.domain.checkin.CheckInFixture.START;
import static com.gommit.domain.checkin.CheckInFixture.dailyChallenge;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeMember;
import com.gommit.domain.challenge.entity.ChallengeStatus;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.checkin.CheckInFixture;
import com.gommit.domain.checkin.dto.request.SubmitCheckInForm;
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
import com.gommit.domain.checkin.support.CheckInGuard;
import com.gommit.domain.checkin.support.CheckInGuard.ReadAccess;
import com.gommit.domain.point.entity.UserPointReason;
import com.gommit.domain.point.service.PointService;
import com.gommit.domain.user.service.UserService;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
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
    private ChallengeMemberRepository challengeMemberRepository;

    @Mock
    private CheckInGuard guard;

    @Mock
    private CheckInPolicy policy;

    @Mock
    private CheckInMediaStore mediaStore;

    @Mock
    private PointService pointService;

    @Mock
    private UserService userService;

    private CheckInService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneId.of("UTC"));
        service = new CheckInService(
                checkInRepository,
                challengeMemberRepository,
                guard,
                policy,
                mediaStore,
                pointService,
                userService,
                clock);
        lenient().when(userService.findNicknames(anyList())).thenReturn(Map.of(USER_ID, "인증러"));
    }

    private MultipartFile media() {
        return new MockMultipartFile("media", "shot.png", "image/png", new byte[] {1, 2, 3});
    }

    private SubmitCheckInForm form(String memo) {
        return new SubmitCheckInForm(CheckInType.PHOTO, memo);
    }

    private void givenActiveMemberAndValidDay(Challenge challenge) {
        ChallengeMember member = CheckInFixture.activeMember(9L, challenge, USER_ID);
        when(guard.getChallenge(CHALLENGE_ID)).thenReturn(challenge);
        when(challengeMemberRepository.findByChallenge_IdAndUserId(CHALLENGE_ID, USER_ID))
                .thenReturn(Optional.of(member));
        when(policy.isCheckInDay(challenge, TODAY)).thenReturn(true);
        when(policy.allows(challenge, CheckInType.PHOTO)).thenReturn(true);
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
            when(mediaStore.reserve(any())).thenReturn("check-ins/2026/09/uuid.png");
            when(policy.checkInReward()).thenReturn(10);
            when(checkInRepository.saveAndFlush(any(CheckIn.class))).thenAnswer(inv -> {
                CheckIn c = inv.getArgument(0);
                ReflectionTestUtils.setField(c, "id", 100L);
                return c;
            });

            CheckInResultResponse result = service.submit(USER_ID, CHALLENGE_ID, form(null), media());

            assertThat(result.currentCount()).isEqualTo(2);
            assertThat(result.targetCount()).isEqualTo(3);
            assertThat(result.dailyCompleted()).isFalse();
            assertThat(result.earnedUserPoints()).isEqualTo(10);
            assertThat(result.checkIn().roundNo()).isEqualTo(2);
            assertThat(result.checkIn().mediaType()).isEqualTo(MediaType.IMAGE);

            // 순서: row 저장(flush) → 포인트 적립 → 미디어 바이트 쓰기.
            var order = inOrder(checkInRepository, pointService, mediaStore);
            order.verify(checkInRepository).saveAndFlush(any(CheckIn.class));
            order.verify(pointService).reward(USER_ID, CHALLENGE_ID, 10, UserPointReason.CHECK_IN, "인증");
            order.verify(mediaStore).write(any(), eq("check-ins/2026/09/uuid.png"));
        }

        @Test
        @DisplayName("목표 회차를 채우면 dailyCompleted=true")
        void completesDaily() {
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 1);
            givenActiveMemberAndValidDay(challenge);
            when(checkInRepository.countByChallengeIdAndUserIdAndBusinessDate(CHALLENGE_ID, USER_ID, TODAY))
                    .thenReturn(0);
            when(mediaStore.reserve(any())).thenReturn("check-ins/2026/09/uuid.png");
            when(checkInRepository.saveAndFlush(any(CheckIn.class))).thenAnswer(inv -> inv.getArgument(0));

            assertThat(service.submit(USER_ID, CHALLENGE_ID, form(null), media())
                            .dailyCompleted())
                    .isTrue();
        }

        @Test
        @DisplayName("진행 중이 아닌 챌린지면 CHALLENGE_NOT_ACTIVE")
        void rejectsInactiveChallenge() {
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 1);
            ReflectionTestUtils.setField(challenge, "status", ChallengeStatus.ENDED);
            when(guard.getChallenge(CHALLENGE_ID)).thenReturn(challenge);

            assertBusiness(
                    () -> service.submit(USER_ID, CHALLENGE_ID, form(null), media()), ErrorCode.CHALLENGE_NOT_ACTIVE);
        }

        @Test
        @DisplayName("참여자 행이 없으면 NOT_CHALLENGE_MEMBER")
        void rejectsNonMember() {
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 1);
            when(guard.getChallenge(CHALLENGE_ID)).thenReturn(challenge);
            when(challengeMemberRepository.findByChallenge_IdAndUserId(CHALLENGE_ID, USER_ID))
                    .thenReturn(Optional.empty());

            assertBusiness(
                    () -> service.submit(USER_ID, CHALLENGE_ID, form(null), media()), ErrorCode.NOT_CHALLENGE_MEMBER);
        }

        @Test
        @DisplayName("이탈한 참여자면 NOT_CHALLENGE_MEMBER")
        void rejectsLeftMember() {
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 1);
            when(guard.getChallenge(CHALLENGE_ID)).thenReturn(challenge);
            when(challengeMemberRepository.findByChallenge_IdAndUserId(CHALLENGE_ID, USER_ID))
                    .thenReturn(Optional.of(CheckInFixture.leftMember(9L, challenge, USER_ID, TODAY.minusDays(1))));

            assertBusiness(
                    () -> service.submit(USER_ID, CHALLENGE_ID, form(null), media()), ErrorCode.NOT_CHALLENGE_MEMBER);
        }

        @Test
        @DisplayName("인증 대상일이 아니면 NOT_CHECK_IN_DAY")
        void rejectsNonCheckInDay() {
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 1);
            ChallengeMember member = CheckInFixture.activeMember(9L, challenge, USER_ID);
            when(guard.getChallenge(CHALLENGE_ID)).thenReturn(challenge);
            when(challengeMemberRepository.findByChallenge_IdAndUserId(CHALLENGE_ID, USER_ID))
                    .thenReturn(Optional.of(member));
            when(policy.isCheckInDay(challenge, TODAY)).thenReturn(false);

            assertBusiness(
                    () -> service.submit(USER_ID, CHALLENGE_ID, form(null), media()), ErrorCode.NOT_CHECK_IN_DAY);
        }

        @Test
        @DisplayName("허용되지 않은 인증 방식이면 CHECK_IN_TYPE_NOT_ALLOWED")
        void rejectsDisallowedType() {
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 1);
            ChallengeMember member = CheckInFixture.activeMember(9L, challenge, USER_ID);
            when(guard.getChallenge(CHALLENGE_ID)).thenReturn(challenge);
            when(challengeMemberRepository.findByChallenge_IdAndUserId(CHALLENGE_ID, USER_ID))
                    .thenReturn(Optional.of(member));
            when(policy.isCheckInDay(challenge, TODAY)).thenReturn(true);
            when(policy.allows(challenge, CheckInType.PHOTO)).thenReturn(false);

            assertBusiness(
                    () -> service.submit(USER_ID, CHALLENGE_ID, form(null), media()),
                    ErrorCode.CHECK_IN_TYPE_NOT_ALLOWED);
        }

        @Test
        @DisplayName("메모가 100자를 넘으면 MEMO_TOO_LONG")
        void rejectsLongMemo() {
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 1);
            ChallengeMember member = CheckInFixture.activeMember(9L, challenge, USER_ID);
            when(guard.getChallenge(CHALLENGE_ID)).thenReturn(challenge);
            when(challengeMemberRepository.findByChallenge_IdAndUserId(CHALLENGE_ID, USER_ID))
                    .thenReturn(Optional.of(member));

            assertBusiness(
                    () -> service.submit(USER_ID, CHALLENGE_ID, form("a".repeat(101)), media()),
                    ErrorCode.MEMO_TOO_LONG);
        }

        @Test
        @DisplayName("이미 목표 회차를 채웠으면 DAILY_LIMIT_EXCEEDED")
        void rejectsWhenLimitReached() {
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 2);
            givenActiveMemberAndValidDay(challenge);
            when(checkInRepository.countByChallengeIdAndUserIdAndBusinessDate(CHALLENGE_ID, USER_ID, TODAY))
                    .thenReturn(2);

            assertBusiness(
                    () -> service.submit(USER_ID, CHALLENGE_ID, form(null), media()), ErrorCode.DAILY_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("uk_check_ins 위반은 DAILY_LIMIT_EXCEEDED 로 변환한다")
        void translatesUniqueViolation() {
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 3);
            givenActiveMemberAndValidDay(challenge);
            when(checkInRepository.countByChallengeIdAndUserIdAndBusinessDate(CHALLENGE_ID, USER_ID, TODAY))
                    .thenReturn(0);
            when(mediaStore.reserve(any())).thenReturn("check-ins/2026/09/uuid.png");
            when(checkInRepository.saveAndFlush(any(CheckIn.class)))
                    .thenThrow(new DataIntegrityViolationException("uk_check_ins"));

            assertBusiness(
                    () -> service.submit(USER_ID, CHALLENGE_ID, form(null), media()), ErrorCode.DAILY_LIMIT_EXCEEDED);

            // insert 가 실패하면 파일을 쓰지 않는다 — orphan 없음.
            verify(mediaStore, never()).write(any(), any());
            verify(pointService, never()).reward(anyLong(), anyLong(), anyInt(), any(), any());
        }
    }

    @Nested
    @DisplayName("getTodayStatus")
    class TodayStatus {

        @Test
        @DisplayName("현재/목표 회차와 대상일 여부, 허용 방식을 준다")
        void returnsStatus() {
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 3);
            when(guard.getChallengeForActiveMember(CHALLENGE_ID, USER_ID)).thenReturn(challenge);
            when(checkInRepository.countByChallengeIdAndUserIdAndBusinessDate(CHALLENGE_ID, USER_ID, TODAY))
                    .thenReturn(2);
            when(policy.isCheckInDay(challenge, TODAY)).thenReturn(true);
            when(policy.allowedTypes(challenge)).thenReturn(List.of(CheckInType.PHOTO));

            TodayCheckInStatusResponse status = service.getTodayStatus(USER_ID, CHALLENGE_ID);

            assertThat(status.businessDate()).isEqualTo(TODAY);
            assertThat(status.currentCount()).isEqualTo(2);
            assertThat(status.targetCount()).isEqualTo(3);
            assertThat(status.completed()).isFalse();
            assertThat(status.isCheckInDay()).isTrue();
            assertThat(status.allowedTypes()).containsExactly(CheckInType.PHOTO);
        }
    }

    @Nested
    @DisplayName("조회 — 접근 제어")
    class Read {

        private final Challenge challenge = dailyChallenge(CHALLENGE_ID, 1);

        @Test
        @DisplayName("갤러리 — ACTIVE 멤버는 maxDate 제한 없이(null) 조회한다")
        void galleryActiveNoDateLimit() {
            when(guard.resolveReadAccess(CHALLENGE_ID, USER_ID)).thenReturn(new ReadAccess(challenge, null));
            when(checkInRepository.findGallery(
                            eq(CHALLENGE_ID), any(), any(), any(), eq(null), any(), any(Pageable.class)))
                    .thenReturn(List.of());

            service.getGallery(USER_ID, CHALLENGE_ID, null, null, null, null, 20);
        }

        @Test
        @DisplayName("갤러리 — 이탈 멤버는 leftAt 이하 날짜로만 조회한다")
        void galleryLeftMemberDateCapped() {
            LocalDate leftOn = TODAY.minusDays(3);
            when(guard.resolveReadAccess(CHALLENGE_ID, USER_ID)).thenReturn(new ReadAccess(challenge, leftOn));
            when(checkInRepository.findGallery(
                            eq(CHALLENGE_ID), any(), any(), any(), eq(leftOn), any(), any(Pageable.class)))
                    .thenReturn(List.of());

            service.getGallery(USER_ID, CHALLENGE_ID, null, null, null, null, 20);
        }

        @Test
        @DisplayName("단건 — 없는 id 는 CHECK_IN_NOT_FOUND")
        void oneNotFound() {
            when(checkInRepository.findById(5L)).thenReturn(Optional.empty());

            assertBusiness(() -> service.getCheckIn(USER_ID, CHALLENGE_ID, 5L), ErrorCode.CHECK_IN_NOT_FOUND);
        }

        @Test
        @DisplayName("단건 — 다른 챌린지의 인증이면 NOT_CHALLENGE_MEMBER")
        void oneWrongChallenge() {
            CheckIn checkIn = checkIn(5L, 999L, TODAY);
            when(checkInRepository.findById(5L)).thenReturn(Optional.of(checkIn));

            assertBusiness(() -> service.getCheckIn(USER_ID, CHALLENGE_ID, 5L), ErrorCode.NOT_CHALLENGE_MEMBER);
        }

        @Test
        @DisplayName("단건 — 이탈일 이후 날짜의 기록이면 NOT_CHALLENGE_MEMBER")
        void oneAfterLeftDate() {
            CheckIn checkIn = checkIn(5L, CHALLENGE_ID, TODAY);
            when(checkInRepository.findById(5L)).thenReturn(Optional.of(checkIn));
            when(guard.resolveReadAccess(CHALLENGE_ID, USER_ID))
                    .thenReturn(new ReadAccess(challenge, TODAY.minusDays(1)));

            assertBusiness(() -> service.getCheckIn(USER_ID, CHALLENGE_ID, 5L), ErrorCode.NOT_CHALLENGE_MEMBER);
        }

        @Test
        @DisplayName("단건 — 참여 기간 내 기록은 조회된다")
        void oneWithinTenure() {
            CheckIn checkIn = checkIn(5L, CHALLENGE_ID, TODAY.minusDays(5));
            when(checkInRepository.findById(5L)).thenReturn(Optional.of(checkIn));
            when(guard.resolveReadAccess(CHALLENGE_ID, USER_ID))
                    .thenReturn(new ReadAccess(challenge, TODAY.minusDays(1)));

            CheckInResponse response = service.getCheckIn(USER_ID, CHALLENGE_ID, 5L);

            assertThat(response.id()).isEqualTo(5L);
        }

        @Test
        @DisplayName("최근 로그 — 한 줄 텍스트를 만들어 준다")
        void recentText() {
            when(guard.resolveReadAccess(CHALLENGE_ID, USER_ID)).thenReturn(new ReadAccess(challenge, null));
            when(checkInRepository.findRecent(eq(CHALLENGE_ID), eq(null), any(Limit.class)))
                    .thenReturn(List.of(checkIn(5L, CHALLENGE_ID, TODAY)));

            RecentCheckInResponse recent = service.getRecent(USER_ID, CHALLENGE_ID, 3);

            assertThat(recent.items()).hasSize(1);
            assertThat(recent.items().get(0).text()).contains("인증러");
            assertThat(recent.items().get(0).earnedUserPoints()).isNull();
        }

        @Test
        @DisplayName("미디어 서빙 — 접근 가능한 인증의 파일을 로드한다")
        void loadMedia() {
            CheckIn checkIn = checkIn(5L, CHALLENGE_ID, TODAY.minusDays(2));
            ReflectionTestUtils.setField(checkIn, "mediaKey", "check-ins/2026/09/uuid.png");
            when(checkInRepository.findById(5L)).thenReturn(Optional.of(checkIn));
            when(guard.resolveReadAccess(CHALLENGE_ID, USER_ID)).thenReturn(new ReadAccess(challenge, null));
            Resource resource = new ByteArrayResource(new byte[] {9});
            when(mediaStore.load("check-ins/2026/09/uuid.png")).thenReturn(resource);

            assertThat(service.loadCheckInMedia(USER_ID, 5L)).isSameAs(resource);
        }

        @Test
        @DisplayName("미디어 서빙 — 이탈일 이후 기록은 NOT_CHALLENGE_MEMBER")
        void loadMediaAfterLeft() {
            CheckIn checkIn = checkIn(5L, CHALLENGE_ID, TODAY);
            when(checkInRepository.findById(5L)).thenReturn(Optional.of(checkIn));
            when(guard.resolveReadAccess(CHALLENGE_ID, USER_ID))
                    .thenReturn(new ReadAccess(challenge, TODAY.minusDays(1)));

            assertBusiness(() -> service.loadCheckInMedia(USER_ID, 5L), ErrorCode.NOT_CHALLENGE_MEMBER);
        }
    }

    @Nested
    @DisplayName("getMyCheckIns")
    class MyCheckIns {

        @Test
        @DisplayName("challengeId 지정 시 존재하지 않으면 CHALLENGE_NOT_FOUND")
        void unknownChallenge() {
            when(guard.getChallenge(CHALLENGE_ID)).thenThrow(new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));

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
        CheckIn checkIn = CheckIn.create(
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
