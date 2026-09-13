package com.gommit.domain.checkin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.gommit.domain.challenge.entity.*;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.challenge.service.ChallengeProgressCalculator;
import com.gommit.domain.checkin.entity.*;
import com.gommit.domain.checkin.repository.CheckInRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GroupCompletionQueryServiceTest {
    private static final LocalDate START = LocalDate.of(2026, 9, 1);

    @Mock
    private CheckInRepository checkInRepository;

    @Mock
    private ChallengeMemberRepository memberRepository;

    @Spy
    private ChallengeProgressCalculator calculator = new ChallengeProgressCalculator();

    @InjectMocks
    private GroupCompletionQueryService service;

    private long nextId = 1;

    private Challenge challenge() {
        Challenge challenge = Challenge.builder()
                .groupId(1L)
                .seqNo(1)
                .startDate(START)
                .endDate(START.plusDays(29))
                .frequencyType(FrequencyType.DAILY)
                .dailyCheckInCount(2)
                .requiredDayCount(30)
                .allowPhoto(true)
                .build();
        ReflectionTestUtils.setField(challenge, "id", 10L);
        challenge.activate();
        return challenge;
    }

    private ChallengeMember member(Challenge challenge, long userId, LocalDateTime joinedAt) {
        ChallengeMember member = new ChallengeMember(challenge, userId, ChallengeMemberRole.MEMBER);
        ReflectionTestUtils.setField(member, "createdAt", joinedAt);
        return member;
    }

    private CheckIn checkIn(long userId, LocalDate date, int round, LocalDateTime at) {
        CheckIn row = new CheckIn(10L, userId, round, CheckInType.PHOTO, "test.webp", MediaType.IMAGE, null, date);
        ReflectionTestUtils.setField(row, "id", nextId++);
        ReflectionTestUtils.setField(row, "createdAt", at);
        return row;
    }

    private void complete(List<CheckIn> rows, long userId, LocalDate date, int hour) {
        rows.add(checkIn(userId, date, 1, date.atTime(hour, 0)));
        rows.add(checkIn(userId, date, 2, date.atTime(hour, 1)));
    }

    private void givenRows(Challenge challenge, LocalDate today, List<ChallengeMember> members, List<CheckIn> rows) {
        LocalDate end = today.isBefore(challenge.getEndDate()) ? today : challenge.getEndDate();
        when(checkInRepository.findGallery(10L, START, end, null, null, null, null, Pageable.unpaged()))
                .thenReturn(rows);
        when(memberRepository.findAllByChallengeId(10L)).thenReturn(members);
    }

    @Test
    void countsEightSuccessfulDaysOutOfElevenWithOnlyTwoRepositoryCalls() {
        Challenge challenge = challenge();
        LocalDate today = START.plusDays(10);
        var members = List.of(
                member(challenge, 1, START.minusDays(1).atStartOfDay()),
                member(challenge, 2, START.minusDays(1).atStartOfDay()));
        List<CheckIn> rows = new ArrayList<>();
        for (int day = 0; day < 11; day++) {
            LocalDate date = START.plusDays(day);
            complete(rows, 1, date, 10);
            rows.add(checkIn(2, date, 1, date.atTime(11, 0)));
            if (day < 8) rows.add(checkIn(2, date, 2, date.atTime(11, 1)));
        }
        Collections.reverse(rows); // 갤러리 정렬(최신순)에 영향받지 않는다.
        givenRows(challenge, today, members, rows);
        assertThat(service.countCompletedDays(challenge, today)).isEqualTo(8);
        verify(checkInRepository).findGallery(10L, START, today, null, null, null, null, Pageable.unpaged());
        verify(memberRepository).findAllByChallengeId(10L);
        verifyNoMoreInteractions(checkInRepository, memberRepository);
    }

    @ParameterizedTest
    @EnumSource(
            value = ChallengeMemberStatus.class,
            names = {"LEFT", "KICKED"})
    void usesMembershipAtSubmissionAndDoesNotTurnPastFailuresIntoSuccesses(ChallengeMemberStatus status) {
        Challenge challenge = challenge();
        var owner = member(challenge, 1, START.minusDays(1).atStartOfDay());
        var former = member(challenge, 2, START.minusDays(1).atStartOfDay());
        ReflectionTestUtils.setField(former, "status", status);
        ReflectionTestUtils.setField(former, "leftAt", START.plusDays(2).atTime(12, 0));
        List<CheckIn> rows = new ArrayList<>();
        complete(rows, 1, START, 10);
        complete(rows, 2, START, 11); // 이탈 전 성공은 유지
        complete(rows, 1, START.plusDays(1), 10); // 이탈 전 미달은 계속 실패
        complete(rows, 1, START.plusDays(2), 10); // 이탈만으로 이 날이 성공으로 바뀌지 않음
        complete(rows, 1, START.plusDays(3), 10); // 이탈 이후에는 남은 멤버만 대상
        givenRows(challenge, START.plusDays(3), List.of(owner, former), rows);
        assertThat(service.countCompletedDays(challenge, START.plusDays(3))).isEqualTo(2);
    }

    @Test
    void submissionAfterDepartureUsesRemainingMembersAndBeforeJoiningExcludesNewMember() {
        Challenge challenge = challenge();
        var owner = member(challenge, 1, START.minusDays(1).atStartOfDay());
        var former = member(challenge, 2, START.minusDays(1).atStartOfDay());
        ReflectionTestUtils.setField(former, "status", ChallengeMemberStatus.LEFT);
        ReflectionTestUtils.setField(former, "leftAt", START.atTime(9, 0));
        var newcomer = member(challenge, 3, START.plusDays(1).atTime(9, 0));
        List<CheckIn> rows = new ArrayList<>();
        complete(rows, 1, START, 10); // 이탈 이후, 신규 참여 이전
        complete(rows, 1, START.plusDays(1), 10); // 신규 참여자 미달
        givenRows(challenge, START.plusDays(1), List.of(owner, former, newcomer), rows);
        assertThat(service.countCompletedDays(challenge, START.plusDays(1))).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(
            value = FrequencyType.class,
            names = {"EVERY_N_DAYS", "DAYS_OF_WEEK"})
    void excludesUnscheduledDatesAndCountsEndedSeasons(FrequencyType frequency) {
        Challenge challenge = challenge();
        challenge.end();
        ReflectionTestUtils.setField(challenge, "frequencyType", frequency);
        ReflectionTestUtils.setField(challenge, "frequencyValue", 2);
        ReflectionTestUtils.setField(challenge, "daysOfWeek", "TUE,THU");
        List<CheckIn> rows = new ArrayList<>();
        for (int day = 0; day < 3; day++) complete(rows, 1, START.plusDays(day), 10);
        LocalDate today = START.plusDays(40);
        givenRows(
                challenge,
                today,
                List.of(member(challenge, 1, START.minusDays(1).atStartOfDay())),
                rows);
        assertThat(service.countCompletedDays(challenge, today)).isEqualTo(2);
        verify(checkInRepository)
                .findGallery(10L, START, challenge.getEndDate(), null, null, null, null, Pageable.unpaged());
    }

    @Test
    void noParticipantsOrNoRecordsCannotCompleteADay() {
        Challenge challenge = challenge();
        List<CheckIn> rows = new ArrayList<>();
        complete(rows, 99, START, 10);
        givenRows(challenge, START, List.of(), rows);
        assertThat(service.countCompletedDays(challenge, START)).isZero();
    }

    @Test
    void beforeStartReturnsZeroWithoutQueries() {
        assertThat(service.countCompletedDays(challenge(), START.minusDays(1))).isZero();
        verifyNoInteractions(checkInRepository, memberRepository);
    }

    @Test
    void countsBusinessDateEvenWhenFinalCheckInIsAfterMidnight() {
        Challenge challenge = challenge();
        var owner = member(challenge, 1, START.minusDays(1).atStartOfDay());
        List<CheckIn> rows = List.of(
                checkIn(1, START, 1, START.atTime(23, 59)),
                checkIn(1, START, 2, START.plusDays(1).atTime(3, 59)));
        givenRows(challenge, START, List.of(owner), rows);
        assertThat(service.countCompletedDays(challenge, START)).isEqualTo(1);
    }
}
