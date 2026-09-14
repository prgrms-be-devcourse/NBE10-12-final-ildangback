package com.gommit.domain.notification;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gommit.domain.challenge.entity.*;
import com.gommit.domain.challenge.repository.*;
import com.gommit.domain.challenge.service.ChallengeProgressCalculator;
import com.gommit.domain.checkin.repository.CheckInRepository;
import com.gommit.domain.notification.entity.*;
import com.gommit.domain.notification.repository.NotificationRepository;
import com.gommit.domain.notification.service.CheckInReminderService;
import com.gommit.global.time.BusinessClock;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CheckInReminderServiceTest {
    private final ChallengeRepository challenges = mock(ChallengeRepository.class);
    private final ChallengeMemberRepository members = mock(ChallengeMemberRepository.class);
    private final CheckInRepository checkIns = mock(CheckInRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final LocalDate date = LocalDate.of(2026, 9, 13);

    private CheckInReminderService service(String instant) {
        return new CheckInReminderService(
                challenges,
                members,
                new ChallengeProgressCalculator(),
                checkIns,
                notifications,
                new BusinessClock(Clock.fixed(Instant.parse(instant), ZoneId.of("Asia/Seoul"))));
    }

    private void fixture(FrequencyType frequency, String days) {
        var challenge = mock(Challenge.class);
        when(challenge.getId()).thenReturn(10L);
        when(challenge.getStartDate()).thenReturn(date.minusDays(1));
        when(challenge.getEndDate()).thenReturn(date.plusDays(5));
        when(challenge.getFrequencyType()).thenReturn(frequency);
        when(challenge.getFrequencyValue()).thenReturn(2);
        when(challenge.getDaysOfWeek()).thenReturn(days);
        when(challenge.getDailyCheckInCount()).thenReturn(3);
        when(challenges.findActiveForReminder()).thenReturn(List.of(challenge));
        var member = mock(ChallengeMember.class);
        when(member.getUserId()).thenReturn(2L);
        when(members.findAllByChallengeIdAndStatus(10L, ChallengeMemberStatus.ACTIVE))
                .thenReturn(List.of(member));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    void completionThreshold(int count) {
        fixture(FrequencyType.DAILY, null);
        when(checkIns.countByChallengeIdAndUserIdAndBusinessDate(10L, 2L, date)).thenReturn(count);
        service("2026-09-13T12:00:00Z").sendReminders();
        if (count >= 3) verifyNoInteractions(notifications);
        else {
            var saved = org.mockito.ArgumentCaptor.forClass(Notification.class);
            verify(notifications).save(saved.capture());
            assertThat(saved.getValue().getType()).isEqualTo(NotificationType.CHECK_IN_REMINDER);
            assertThat(saved.getValue().getUserId()).isEqualTo(2L);
            assertThat(saved.getValue().getRefId()).isEqualTo(10L);
            assertThat(saved.getValue().getBody()).isEqualTo("오늘 인증이 아직 남아있어요! 잊기 전에 인증해 주세요 🔥");
        }
    }

    @ParameterizedTest
    @CsvSource({"EVERY_N_DAYS,MON,false", "DAYS_OF_WEEK,MON,false", "DAYS_OF_WEEK,SUN,true"})
    void scheduledDays(FrequencyType frequency, String days, boolean expected) {
        fixture(frequency, days);
        service("2026-09-13T12:00:00Z").sendReminders();
        verify(notifications, times(expected ? 1 : 0)).save(any());
        if (!expected) verifyNoInteractions(checkIns);
    }

    @ParameterizedTest
    @CsvSource({"2026-09-13T18:59:59Z,2026-09-13", "2026-09-13T19:00:00Z,2026-09-14"})
    void businessDayAndDuplicate(String instant, LocalDate businessDate) {
        fixture(FrequencyType.DAILY, null);
        when(notifications.existsByUserIdAndTypeAndRefIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        2L,
                        NotificationType.CHECK_IN_REMINDER,
                        10L,
                        businessDate.atTime(4, 0),
                        businessDate.plusDays(1).atTime(4, 0)))
                .thenReturn(true);
        service(instant).sendReminders();
        verify(checkIns).countByChallengeIdAndUserIdAndBusinessDate(10L, 2L, businessDate);
        verify(notifications, never()).save(any());
    }

    @Test
    void noActiveChallenges() {
        service("2026-09-13T12:00:00Z").sendReminders();
        verifyNoInteractions(members, checkIns, notifications);
    }

    @Test
    void noActiveMembers() {
        fixture(FrequencyType.DAILY, null);
        when(members.findAllByChallengeIdAndStatus(10L, ChallengeMemberStatus.ACTIVE))
                .thenReturn(List.of());
        service("2026-09-13T12:00:00Z").sendReminders();
        verifyNoInteractions(checkIns, notifications);
    }
}
