package com.gommit.domain.challenge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeMember;
import com.gommit.domain.challenge.entity.ChallengeMemberRole;
import com.gommit.domain.challenge.entity.ChallengeMemberStatus;
import com.gommit.domain.challenge.entity.FrequencyType;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChallengeMemberService — 유저 기준 직전 인증 의무일")
class ChallengeMemberServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate START = LocalDate.of(2026, 9, 1); // 화
    private static final LocalDate MON = LocalDate.of(2026, 9, 7);
    private static final LocalDate WED = LocalDate.of(2026, 9, 9);
    private static final LocalDate THU = LocalDate.of(2026, 9, 10);

    @Mock
    private ChallengeMemberRepository challengeMemberRepository;

    private ChallengeMemberService service;

    @BeforeEach
    void setUp() {
        service = new ChallengeMemberService(challengeMemberRepository, new ChallengeProgressCalculator());
    }

    private Challenge challenge(FrequencyType type, Integer frequencyValue, String daysOfWeek) {
        Challenge challenge = Challenge.builder()
                .groupId(1L)
                .seqNo(1)
                .startDate(START)
                .endDate(START.plusDays(60))
                .frequencyType(type)
                .frequencyValue(frequencyValue)
                .daysOfWeek(daysOfWeek)
                .dailyCheckInCount(1)
                .requiredDayCount(61)
                .groupCurrentStreak(0)
                .groupBestStreak(0)
                .allowPhoto(true)
                .build();
        challenge.activate();
        return challenge;
    }

    private Challenge daily() {
        return challenge(FrequencyType.DAILY, null, null);
    }

    private Challenge monWed() {
        return challenge(FrequencyType.DAYS_OF_WEEK, null, "MON,WED");
    }

    private ChallengeMember member(Challenge challenge) {
        return ChallengeMember.builder()
                .challenge(challenge)
                .userId(USER_ID)
                .role(ChallengeMemberRole.MEMBER)
                .build();
    }

    private void stubMemberships(ChallengeMember... members) {
        when(challengeMemberRepository.findAllByUserIdAndStatus(USER_ID, ChallengeMemberStatus.ACTIVE))
                .thenReturn(List.of(members));
    }

    @Test
    @DisplayName("속한 챌린지가 없으면 null")
    void nullWhenNoMembership() {
        stubMemberships();

        assertThat(service.findLastRequiredCheckInDay(USER_ID, THU)).isNull();
    }

    @Test
    @DisplayName("매일 챌린지면 어제")
    void dailyChallengeReturnsYesterday() {
        stubMemberships(member(daily()));

        assertThat(service.findLastRequiredCheckInDay(USER_ID, THU)).isEqualTo(WED);
    }

    @Test
    @DisplayName("월수 챌린지에서 수요일에 물으면 직전 월요일 (화요일은 의무일이 아니다)")
    void daysOfWeekSkipsNonRequiredDays() {
        stubMemberships(member(monWed()));

        assertThat(service.findLastRequiredCheckInDay(USER_ID, WED)).isEqualTo(MON);
    }

    @Test
    @DisplayName("여러 챌린지면 그중 가장 늦은 의무일")
    void picksLatestAcrossChallenges() {
        // 7일 주기는 9/1, 9/8 이 대상일이라 9/10 직전은 9/8. 월수는 9/9.
        stubMemberships(member(challenge(FrequencyType.EVERY_N_DAYS, 7, null)), member(monWed()));

        assertThat(service.findLastRequiredCheckInDay(USER_ID, THU)).isEqualTo(WED);
    }

    @Test
    @DisplayName("챌린지가 ACTIVE 가 아니면 세지 않는다")
    void ignoresChallengeNotActive() {
        Challenge ended = daily();
        ended.end();
        stubMemberships(member(ended));

        assertThat(service.findLastRequiredCheckInDay(USER_ID, THU)).isNull();
    }

    @Test
    @DisplayName("챌린지 시작일 당일에는 직전 의무일이 없다")
    void nullOnChallengeStartDate() {
        stubMemberships(member(daily()));

        assertThat(service.findLastRequiredCheckInDay(USER_ID, START)).isNull();
    }
}
