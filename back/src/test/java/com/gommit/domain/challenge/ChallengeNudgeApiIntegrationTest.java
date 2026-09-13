package com.gommit.domain.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gommit.domain.challenge.entity.*;
import com.gommit.domain.challenge.repository.*;
import com.gommit.domain.checkin.entity.*;
import com.gommit.domain.checkin.repository.CheckInRepository;
import com.gommit.domain.group.entity.*;
import com.gommit.domain.group.repository.ChallengeGroupRepository;
import com.gommit.domain.notification.entity.NotificationType;
import com.gommit.domain.notification.repository.NotificationRepository;
import com.gommit.global.time.BusinessClock;
import com.gommit.support.IntegrationTestSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class ChallengeNudgeApiIntegrationTest extends IntegrationTestSupport {
    @Autowired
    ChallengeGroupRepository groups;

    @Autowired
    ChallengeRepository challenges;

    @Autowired
    ChallengeMemberRepository members;

    @Autowired
    CheckInRepository checkIns;

    @Autowired
    NotificationRepository notifications;

    @MockitoBean
    BusinessClock clock;

    private final LocalDate today = LocalDate.of(2026, 9, 13);
    private Tokens sender;
    private Long senderId;
    private Long receiverId;
    private Challenge challenge;

    @BeforeEach
    void fixture() {
        when(clock.today()).thenReturn(today);
        sender = loginAs();
        loginAs("receiver@example.com", "받는사람");
        senderId =
                jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, "tester@example.com");
        receiverId =
                jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, "receiver@example.com");
        var group = groups.saveAndFlush(ChallengeGroup.builder()
                .name("테스트")
                .category(GroupCategory.EXERCISE)
                .mapType(MapType.GYM)
                .visibility(Visibility.PUBLIC)
                .maxMembers(6)
                .ownerId(senderId)
                .build());
        challenge = Challenge.builder()
                .groupId(group.getId())
                .seqNo(1)
                .startDate(today)
                .endDate(today.plusDays(7))
                .frequencyType(FrequencyType.DAILY)
                .dailyCheckInCount(3)
                .requiredDayCount(8)
                .allowPhoto(true)
                .build();
        challenge.activate();
        challenge = challenges.saveAndFlush(challenge);
        members.saveAndFlush(ChallengeMember.builder()
                .challenge(challenge)
                .userId(senderId)
                .role(ChallengeMemberRole.OWNER)
                .build());
        members.saveAndFlush(ChallengeMember.builder()
                .challenge(challenge)
                .userId(receiverId)
                .role(ChallengeMemberRole.MEMBER)
                .build());
    }

    private org.springframework.test.web.servlet.ResultActions nudge(Long target) throws Exception {
        return mockMvc.perform(withToken(
                post("/api/challenges/{id}/members/{user}/nudge", challenge.getId(), target), sender.accessToken()));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void completionPolicyAndPayload(int count) throws Exception {
        for (int round = 1; round <= count; round++) {
            checkIns.saveAndFlush(new CheckIn(
                    challenge.getId(), receiverId, round, CheckInType.PHOTO, "test.jpg", MediaType.IMAGE, null, today));
        }
        if (count == 3) {
            nudge(receiverId).andExpect(jsonPath("$.code").value("ALREADY_CHECKED_IN"));
            assertThat(notifications.count()).isZero();
        } else {
            nudge(receiverId).andExpect(status().isNoContent());
            var saved = notifications.findAll().getFirst();
            assertThat(saved.getUserId()).isEqualTo(receiverId);
            assertThat(saved.getType()).isEqualTo(NotificationType.CHECK_IN_NUDGE);
            assertThat(saved.getRefId()).isEqualTo(challenge.getId());
            assertThat(saved.getBody()).isEqualTo("테스터님이 오늘 인증을 기다리고 있어요.");
            jdbcTemplate.update("UPDATE notifications SET created_at = ?", today.atTime(12, 0));
            nudge(receiverId).andExpect(jsonPath("$.code").value("ALREADY_NUDGED"));
            assertThat(notifications.count()).isEqualTo(1);
        }
    }

    @Test
    void selfAndUnauthenticated() throws Exception {
        nudge(senderId).andExpect(jsonPath("$.code").value("CANNOT_NUDGE_SELF"));
        mockMvc.perform(post("/api/challenges/{id}/members/{user}/nudge", challenge.getId(), receiverId))
                .andExpect(status().isUnauthorized());
        assertThat(notifications.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"READY", "ENDED"})
    void inactiveChallenge(String state) throws Exception {
        jdbcTemplate.update("UPDATE challenges SET status = ? WHERE id = ?", state, challenge.getId());
        nudge(receiverId).andExpect(jsonPath("$.code").value("CHALLENGE_NOT_ACTIVE"));
        assertThat(notifications.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"LEFT", "KICKED"})
    void inactiveMembers(String state) throws Exception {
        jdbcTemplate.update("UPDATE challenge_members SET status = ? WHERE user_id = ?", state, receiverId);
        nudge(receiverId).andExpect(jsonPath("$.code").value("CHALLENGE_NOT_MEMBER"));
        jdbcTemplate.update("UPDATE challenge_members SET status = 'ACTIVE' WHERE user_id = ?", receiverId);
        jdbcTemplate.update("UPDATE challenge_members SET status = ? WHERE user_id = ?", state, senderId);
        nudge(receiverId).andExpect(jsonPath("$.code").value("CHALLENGE_NOT_MEMBER"));
        assertThat(notifications.count()).isZero();
    }

    @Test
    void everyTwoDaysRejectsSeptember13BeforeDuplicateError() throws Exception {
        jdbcTemplate.update(
                "UPDATE challenges SET start_date = ?, frequency_type = 'EVERY_N_DAYS', frequency_value = 2 WHERE id = ?",
                today.minusDays(1),
                challenge.getId());
        notifications.saveAndFlush(new com.gommit.domain.notification.entity.Notification(
                receiverId, NotificationType.CHECK_IN_NUDGE, "기존 알림", "본문", challenge.getId()));
        jdbcTemplate.update("UPDATE notifications SET created_at = ?", today.atTime(12, 0));
        nudge(receiverId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_CHECK_IN_DAY"));
        assertThat(notifications.count()).isEqualTo(1);
    }

    @Test
    void sundayScheduleAllowsSeptember13ThenRejectsDuplicate() throws Exception {
        jdbcTemplate.update(
                "UPDATE challenges SET frequency_type = 'DAYS_OF_WEEK', days_of_week = 'SUN,TUE,THU' WHERE id = ?",
                challenge.getId());
        nudge(receiverId).andExpect(status().isNoContent());
        jdbcTemplate.update("UPDATE notifications SET created_at = ?", today.atTime(12, 0));
        nudge(receiverId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_NUDGED"));
        assertThat(notifications.count()).isEqualTo(1);
    }
}
