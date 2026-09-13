package com.gommit.domain.notification;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gommit.domain.challenge.entity.*;
import com.gommit.domain.challenge.repository.*;
import com.gommit.domain.challenge.service.ChallengeProgressCalculator;
import com.gommit.domain.notification.entity.Notification;
import com.gommit.domain.notification.entity.NotificationType;
import com.gommit.domain.notification.repository.NotificationRepository;
import com.gommit.domain.notification.service.NotificationService;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.time.BusinessClock;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NotificationServiceTest {
    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final ChallengeRepository challenges = mock(ChallengeRepository.class);
    private final ChallengeMemberRepository members = mock(ChallengeMemberRepository.class);

    private NotificationService serviceAt(BusinessClock clock) {
        return new NotificationService(repository, challenges, members, new ChallengeProgressCalculator(), clock);
    }

    private final NotificationService service = serviceAt(mock(BusinessClock.class));

    private Challenge activeChallenge(FrequencyType frequency, Integer interval, String days) {
        var challenge = Challenge.builder()
                .groupId(1L)
                .seqNo(1)
                .startDate(java.time.LocalDate.of(2026, 9, 12))
                .endDate(java.time.LocalDate.of(2026, 9, 30))
                .frequencyType(frequency)
                .frequencyValue(interval)
                .daysOfWeek(days)
                .dailyCheckInCount(3)
                .build();
        challenge.activate();
        when(challenges.findById(106L)).thenReturn(Optional.of(challenge));
        when(members.existsActiveMember(106L, 2L, ChallengeMemberStatus.ACTIVE)).thenReturn(true);
        return challenge;
    }

    @Test
    void mapsOwnNotificationsToResponse() {
        var notification = new Notification(1L, NotificationType.CHECK_IN_NUDGE, "제목", "본문", 106L);
        when(repository.findAllByUserIdAndReadAtIsNullOrderByCreatedAtDescIdDesc(1L))
                .thenReturn(List.of(notification));
        var responses = service.getNotifications(1L);
        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().type()).isEqualTo(NotificationType.CHECK_IN_NUDGE);
        assertThat(responses.getFirst().title()).isEqualTo("제목");
        assertThat(responses.getFirst().body()).isEqualTo("본문");
        assertThat(responses.getFirst().refId()).isEqualTo(106L);
        assertThat(responses.getFirst().readAt()).isNull();
        verify(repository).findAllByUserIdAndReadAtIsNullOrderByCreatedAtDescIdDesc(1L);
    }

    @Test
    void readPreservesFirstTimestamp() {
        var notification = new Notification(1L, NotificationType.CHECK_IN_NUDGE, "제목", "본문", 106L);
        when(repository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(notification));
        service.readNotification(1L, 10L);
        var readAt = notification.getReadAt();
        assertThat(readAt).isNotNull();
        service.readNotification(1L, 10L);
        assertThat(notification.getReadAt()).isEqualTo(readAt);
    }

    @Test
    void rejectsMissingOrForeignIdThroughOwnerScopedLookup() {
        when(repository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.readNotification(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("알림을 찾을 수 없습니다.")
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(com.gommit.global.exception.ErrorCode.NOTIFICATION_NOT_FOUND));
        verify(repository).findByIdAndUserId(10L, 1L);
        verifyNoMoreInteractions(repository);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"2026-09-13T18:59:59Z,2026-09-13", "2026-09-13T19:00:00Z,2026-09-14"})
    void nudgeUsesKstBusinessDayAndStoresPayload(String instant, String date) {
        var clock = new BusinessClock(
                java.time.Clock.fixed(java.time.Instant.parse(instant), java.time.ZoneId.of("Asia/Seoul")));
        activeChallenge(FrequencyType.DAILY, null, null);
        var sender = serviceAt(clock);
        var start = java.time.LocalDate.parse(date).atTime(4, 0);
        sender.sendCheckInNudge(2L, "보낸사람", 106L);
        verify(repository)
                .existsByUserIdAndTypeAndRefIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        2L, NotificationType.CHECK_IN_NUDGE, 106L, start, start.plusDays(1));
        var saved = org.mockito.ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(2L);
        assertThat(saved.getValue().getType()).isEqualTo(NotificationType.CHECK_IN_NUDGE);
        assertThat(saved.getValue().getRefId()).isEqualTo(106L);
        assertThat(saved.getValue().getTitle()).isEqualTo("콕 찌르기가 도착했어요!");
        assertThat(saved.getValue().getBody()).isEqualTo("보낸사람님이 오늘 인증을 기다리고 있어요.");
        assertThat(saved.getValue().getReadAt()).isNull();
    }

    @Test
    void duplicateNudgeDoesNotSave() {
        var clock = mock(BusinessClock.class);
        activeChallenge(FrequencyType.DAILY, null, null);
        var date = java.time.LocalDate.of(2026, 9, 13);
        when(clock.today()).thenReturn(date);
        when(repository.existsByUserIdAndTypeAndRefIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        2L,
                        NotificationType.CHECK_IN_NUDGE,
                        106L,
                        date.atTime(4, 0),
                        date.plusDays(1).atTime(4, 0)))
                .thenReturn(true);
        assertThatThrownBy(() -> serviceAt(clock).sendCheckInNudge(2L, "다른보낸사람", 106L))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(com.gommit.global.exception.ErrorCode.ALREADY_NUDGED));
        verify(repository, never()).save(any());
    }

    @Test
    void nonCheckInDayStopsBeforeDuplicateLookup() {
        activeChallenge(FrequencyType.EVERY_N_DAYS, 2, null);
        var clock = mock(BusinessClock.class);
        when(clock.today()).thenReturn(java.time.LocalDate.of(2026, 9, 13));
        assertThatThrownBy(() -> serviceAt(clock).sendCheckInNudge(2L, "보낸사람", 106L))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(com.gommit.global.exception.ErrorCode.NOT_CHECK_IN_DAY));
        verifyNoInteractions(repository);
    }

    @Test
    void inactiveReceiverStopsBeforeDuplicateLookup() {
        activeChallenge(FrequencyType.EVERY_N_DAYS, 2, null);
        when(members.existsActiveMember(106L, 2L, ChallengeMemberStatus.ACTIVE)).thenReturn(false);
        assertThatThrownBy(() -> serviceAt(mock(BusinessClock.class)).sendCheckInNudge(2L, "보낸사람", 106L))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(com.gommit.global.exception.ErrorCode.CHALLENGE_NOT_MEMBER));
        verifyNoInteractions(repository);
    }

    @Test
    void inactiveChallengeCannotSendDirectly() {
        activeChallenge(FrequencyType.DAILY, null, null).end();
        assertThatThrownBy(() -> serviceAt(mock(BusinessClock.class)).sendCheckInNudge(2L, "보낸사람", 106L))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(com.gommit.global.exception.ErrorCode.CHALLENGE_NOT_ACTIVE));
        verifyNoInteractions(repository);
    }
}
