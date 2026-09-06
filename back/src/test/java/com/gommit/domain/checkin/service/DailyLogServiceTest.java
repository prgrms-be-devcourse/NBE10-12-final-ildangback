package com.gommit.domain.checkin.service;

import static com.gommit.domain.checkin.CheckInFixture.dailyChallenge;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeMemberStatus;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.checkin.dto.response.DailyLogCursorResponse;
import com.gommit.domain.checkin.dto.response.DailyLogResponse;
import com.gommit.domain.checkin.entity.DailyLog;
import com.gommit.domain.checkin.event.DailyLogCompletedEvent;
import com.gommit.domain.checkin.media.DailyLogMediaStore;
import com.gommit.domain.checkin.repository.CheckInRepository;
import com.gommit.domain.checkin.repository.DailyLogRepository;
import com.gommit.domain.checkin.support.CheckInPreconditions;
import com.gommit.domain.checkin.support.CheckInPreconditions.ReadDateAccess;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("DailyLogService")
class DailyLogServiceTest {

    private static final long CHALLENGE_ID = 1L;
    private static final long USER_ID = 42L;
    private static final LocalDate DATE = LocalDate.of(2026, 9, 5);

    @Mock
    private DailyLogRepository dailyLogRepository;

    @Mock
    private CheckInRepository checkInRepository;

    @Mock
    private ChallengeMemberRepository challengeMemberRepository;

    @Mock
    private CheckInPreconditions preconditions;

    @Mock
    private DailyLogMediaStore mediaStore;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private DailyLogService service;

    @BeforeEach
    void setUp() {
        service = new DailyLogService(
                dailyLogRepository, checkInRepository, challengeMemberRepository, preconditions, mediaStore, eventPublisher);
    }

    private static DailyLog logWithId(Long id, Long challengeId, LocalDate date) {
        DailyLog log = DailyLog.create(challengeId, date);
        ReflectionTestUtils.setField(log, "id", id);
        return log;
    }

    private void givenSnapshotTotal(int target, List<Long> completedUserIds, long total) {
        when(checkInRepository.findCompletedUserIds(CHALLENGE_ID, DATE, target)).thenReturn(completedUserIds);
        when(challengeMemberRepository.countSnapshotMembers(
                        eq(CHALLENGE_ID), any(), any(), eq(ChallengeMemberStatus.ACTIVE)))
                .thenReturn(total);
    }

    @Nested
    @DisplayName("getDailyLogs")
    class GetDailyLogs {

        @Test
        @DisplayName("row 를 커서 응답으로 매핑하고 completedCount/totalCount 를 계산한다")
        void mapsToResponse() {
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 2);
            when(preconditions.resolveReadDateAccess(CHALLENGE_ID, USER_ID)).thenReturn(new ReadDateAccess(challenge, null));
            when(dailyLogRepository.findLogs(eq(CHALLENGE_ID), isNull(), isNull(), any()))
                    .thenReturn(List.of(logWithId(2L, CHALLENGE_ID, DATE)));
            givenSnapshotTotal(2, List.of(10L, 11L), 2L);

            DailyLogCursorResponse result = service.getDailyLogs(USER_ID, CHALLENGE_ID, null, 20);

            assertThat(result.content()).hasSize(1);
            DailyLogResponse response = result.content().get(0);
            assertThat(response.id()).isEqualTo(2L);
            assertThat(response.businessDate()).isEqualTo(DATE);
            assertThat(response.completedCount()).isEqualTo(2);
            assertThat(response.totalCount()).isEqualTo(2);
            assertThat(response.videoUrl()).isNull(); // videoKey 없음
            assertThat(result.meta().hasNext()).isFalse();
        }

        @Test
        @DisplayName("videoKey 가 있으면 videoUrl 을 채운다")
        void includesVideoUrlWhenPresent() {
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 1);
            when(preconditions.resolveReadDateAccess(CHALLENGE_ID, USER_ID)).thenReturn(new ReadDateAccess(challenge, null));
            DailyLog log = logWithId(5L, CHALLENGE_ID, DATE);
            log.attachVideo("daily-check-ins/2026/09/uuid.mp4");
            when(dailyLogRepository.findLogs(eq(CHALLENGE_ID), isNull(), isNull(), any()))
                    .thenReturn(List.of(log));
            givenSnapshotTotal(1, List.of(10L), 1L);

            DailyLogResponse response = service.getDailyLogs(USER_ID, CHALLENGE_ID, null, 20)
                    .content()
                    .get(0);

            assertThat(response.videoUrl()).isEqualTo("/api/daily-logs/5/media");
        }
    }

    @Nested
    @DisplayName("getDailyLog")
    class GetDailyLog {

        @Test
        @DisplayName("row 없으면 DAILY_LOG_NOT_FOUND")
        void notFound() {
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 1);
            when(preconditions.resolveReadDateAccess(CHALLENGE_ID, USER_ID)).thenReturn(new ReadDateAccess(challenge, null));
            when(dailyLogRepository.findByChallengeIdAndLogDate(CHALLENGE_ID, DATE))
                    .thenReturn(Optional.empty());

            assertBusiness(() -> service.getDailyLog(USER_ID, CHALLENGE_ID, DATE), ErrorCode.DAILY_LOG_NOT_FOUND);
        }

        @Test
        @DisplayName("이탈일 이후 조회는 row 존재와 무관하게 NOT_CHALLENGE_MEMBER")
        void forbiddenAfterLeftDate() {
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 1);
            when(preconditions.resolveReadDateAccess(CHALLENGE_ID, USER_ID))
                    .thenReturn(new ReadDateAccess(challenge, DATE.minusDays(1)));

            assertBusiness(() -> service.getDailyLog(USER_ID, CHALLENGE_ID, DATE), ErrorCode.NOT_CHALLENGE_MEMBER);
        }

        @Test
        @DisplayName("성공")
        void succeeds() {
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 1);
            when(preconditions.resolveReadDateAccess(CHALLENGE_ID, USER_ID)).thenReturn(new ReadDateAccess(challenge, null));
            when(dailyLogRepository.findByChallengeIdAndLogDate(CHALLENGE_ID, DATE))
                    .thenReturn(Optional.of(logWithId(3L, CHALLENGE_ID, DATE)));
            givenSnapshotTotal(1, List.of(), 3L);

            DailyLogResponse response = service.getDailyLog(USER_ID, CHALLENGE_ID, DATE);

            assertThat(response.id()).isEqualTo(3L);
            assertThat(response.completedCount()).isZero();
            assertThat(response.totalCount()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("recordCheckIn")
    class RecordCheckIn {

        @Test
        @DisplayName("그 날 첫 인증이면 row 를 만든다")
        void createsRowOnFirstCheckIn() {
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 3);
            when(dailyLogRepository.existsByChallengeIdAndLogDate(CHALLENGE_ID, DATE))
                    .thenReturn(false);
            givenSnapshotTotal(3, List.of(), 5L);

            service.recordCheckIn(challenge, DATE);

            verify(dailyLogRepository).saveAndFlush(any(DailyLog.class));
        }

        @Test
        @DisplayName("이미 row 가 있으면 다시 만들지 않는다")
        void skipsWhenRowExists() {
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 3);
            when(dailyLogRepository.existsByChallengeIdAndLogDate(CHALLENGE_ID, DATE))
                    .thenReturn(true);
            givenSnapshotTotal(3, List.of(), 5L);

            service.recordCheckIn(challenge, DATE);

            verify(dailyLogRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("동시 생성 경합(uk_daily_logs 위반)은 무시한다")
        void swallowsRaceOnCreate() {
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 3);
            when(dailyLogRepository.existsByChallengeIdAndLogDate(CHALLENGE_ID, DATE))
                    .thenReturn(false);
            when(dailyLogRepository.saveAndFlush(any(DailyLog.class)))
                    .thenThrow(new DataIntegrityViolationException("uk_daily_logs"));
            givenSnapshotTotal(3, List.of(), 5L);

            service.recordCheckIn(challenge, DATE); // 예외 없이 정상 반환되어야 한다
        }

        @Test
        @DisplayName("전원 완료(completed == total > 0) 시 완료 이벤트를 발행한다")
        void publishesCompletedEvent() {
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 1);
            when(dailyLogRepository.existsByChallengeIdAndLogDate(CHALLENGE_ID, DATE))
                    .thenReturn(true);
            givenSnapshotTotal(1, List.of(10L, 11L), 2L);

            service.recordCheckIn(challenge, DATE);

            verify(eventPublisher).publishEvent(new DailyLogCompletedEvent(CHALLENGE_ID, DATE));
        }

        @Test
        @DisplayName("아직 미완료면 이벤트를 발행하지 않는다")
        void doesNotPublishWhenIncomplete() {
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 1);
            when(dailyLogRepository.existsByChallengeIdAndLogDate(CHALLENGE_ID, DATE))
                    .thenReturn(true);
            givenSnapshotTotal(1, List.of(10L), 2L);

            service.recordCheckIn(challenge, DATE);

            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("totalCount 가 0 이면 이벤트를 발행하지 않는다")
        void doesNotPublishWhenTotalIsZero() {
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 1);
            when(dailyLogRepository.existsByChallengeIdAndLogDate(CHALLENGE_ID, DATE))
                    .thenReturn(true);
            givenSnapshotTotal(1, List.of(), 0L);

            service.recordCheckIn(challenge, DATE);

            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("loadMedia")
    class LoadMedia {

        @Test
        @DisplayName("row 없으면 DAILY_LOG_NOT_FOUND")
        void rowMissing() {
            when(dailyLogRepository.findById(7L)).thenReturn(Optional.empty());

            assertBusiness(() -> service.loadMedia(USER_ID, 7L), ErrorCode.DAILY_LOG_NOT_FOUND);
        }

        @Test
        @DisplayName("이탈일 이후면 NOT_CHALLENGE_MEMBER")
        void forbiddenAfterLeftDate() {
            when(dailyLogRepository.findById(7L)).thenReturn(Optional.of(logWithId(7L, CHALLENGE_ID, DATE)));
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 1);
            when(preconditions.resolveReadDateAccess(CHALLENGE_ID, USER_ID))
                    .thenReturn(new ReadDateAccess(challenge, DATE.minusDays(1)));

            assertBusiness(() -> service.loadMedia(USER_ID, 7L), ErrorCode.NOT_CHALLENGE_MEMBER);
        }

        @Test
        @DisplayName("videoKey 없으면 MEDIA_NOT_FOUND")
        void noVideoYet() {
            when(dailyLogRepository.findById(7L)).thenReturn(Optional.of(logWithId(7L, CHALLENGE_ID, DATE)));
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 1);
            when(preconditions.resolveReadDateAccess(CHALLENGE_ID, USER_ID)).thenReturn(new ReadDateAccess(challenge, null));

            assertBusiness(() -> service.loadMedia(USER_ID, 7L), ErrorCode.MEDIA_NOT_FOUND);
        }

        @Test
        @DisplayName("성공 — mediaStore 에서 로드")
        void succeeds() {
            DailyLog log = logWithId(7L, CHALLENGE_ID, DATE);
            log.attachVideo("daily-check-ins/2026/09/uuid.mp4");
            when(dailyLogRepository.findById(7L)).thenReturn(Optional.of(log));
            Challenge challenge = dailyChallenge(CHALLENGE_ID, 1);
            when(preconditions.resolveReadDateAccess(CHALLENGE_ID, USER_ID)).thenReturn(new ReadDateAccess(challenge, null));
            Resource resource = new ByteArrayResource(new byte[] {1, 2, 3});
            when(mediaStore.load("daily-check-ins/2026/09/uuid.mp4")).thenReturn(resource);

            assertThat(service.loadMedia(USER_ID, 7L)).isSameAs(resource);
        }
    }

    private void assertBusiness(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }
}
