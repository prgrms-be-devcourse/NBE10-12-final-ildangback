package com.gommit.domain.challenge.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gommit.domain.challenge.entity.*;
import com.gommit.domain.challenge.repository.*;
import com.gommit.domain.checkin.repository.CheckInRepository;
import com.gommit.domain.notification.service.NotificationService;
import com.gommit.domain.user.entity.User;
import com.gommit.domain.user.repository.UserRepository;
import com.gommit.global.exception.*;
import com.gommit.global.time.BusinessClock;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChallengeMemberNudgeTest {
    private final ChallengeMemberRepository members = mock(ChallengeMemberRepository.class);
    private final ChallengeRepository challenges = mock(ChallengeRepository.class);
    private final CheckInRepository checkIns = mock(CheckInRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final BusinessClock clock =
            new BusinessClock(Clock.fixed(Instant.parse("2026-09-13T18:59:59Z"), ZoneId.of("Asia/Seoul")));
    private final ChallengeMemberService service = new ChallengeMemberService(
            members, new ChallengeProgressCalculator(), challenges, checkIns, users, notifications, clock);
    private final Challenge challenge = mock(Challenge.class);

    private void active() {
        when(challenges.findById(10L)).thenReturn(Optional.of(challenge));
        when(challenge.getStatus()).thenReturn(ChallengeStatus.ACTIVE);
        when(challenge.getDailyCheckInCount()).thenReturn(3);
    }

    private void joined() {
        active();
        when(members.existsActiveMember(10L, 1L, ChallengeMemberStatus.ACTIVE)).thenReturn(true);
        when(members.existsActiveMember(10L, 2L, ChallengeMemberStatus.ACTIVE)).thenReturn(true);
    }

    private void rejects(ErrorCode code, Long receiver) {
        assertThatThrownBy(() -> service.nudgeMember(10L, 1L, receiver))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(code));
        verifyNoInteractions(notifications);
    }

    @Test
    void missingChallenge() {
        rejects(ErrorCode.CHALLENGE_NOT_FOUND, 2L);
    }

    @Test
    void self() {
        active();
        rejects(ErrorCode.CANNOT_NUDGE_SELF, 1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"READY", "ENDED"})
    void inactive(String status) {
        active();
        when(challenge.getStatus()).thenReturn(ChallengeStatus.valueOf(status));
        rejects(ErrorCode.CHALLENGE_NOT_ACTIVE, 2L);
    }

    @Test
    void inactiveSender() {
        active();
        rejects(ErrorCode.CHALLENGE_NOT_MEMBER, 2L);
    }

    @Test
    void inactiveReceiver() {
        active();
        when(members.existsActiveMember(10L, 1L, ChallengeMemberStatus.ACTIVE)).thenReturn(true);
        rejects(ErrorCode.CHALLENGE_NOT_MEMBER, 2L);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void partialCompletionCanBeNudged(int count) {
        joined();
        when(checkIns.countByChallengeIdAndUserIdAndBusinessDate(10L, 2L, LocalDate.of(2026, 9, 13)))
                .thenReturn(count);
        User sender = mock(User.class);
        when(sender.getNickname()).thenReturn("보낸사람");
        when(users.findById(1L)).thenReturn(Optional.of(sender));
        service.nudgeMember(10L, 1L, 2L);
        verify(checkIns).countByChallengeIdAndUserIdAndBusinessDate(10L, 2L, LocalDate.of(2026, 9, 13));
        verify(notifications).sendCheckInNudge(2L, "보낸사람", 10L);
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4})
    void completed(int count) {
        joined();
        when(checkIns.countByChallengeIdAndUserIdAndBusinessDate(10L, 2L, LocalDate.of(2026, 9, 13)))
                .thenReturn(count);
        rejects(ErrorCode.ALREADY_CHECKED_IN, 2L);
    }
}
