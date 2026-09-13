package com.gommit.domain.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gommit.global.time.BusinessClock;
import com.gommit.support.IntegrationTestSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

@DisplayName("유저 전역 스트릭")
class UserStreakIntegrationTest extends IntegrationTestSupport {

    private static final String EMAIL = "streak@example.com";
    private static final String NICKNAME = "스트릭러";
    // 1x1 PNG (매직바이트 포함) — 저장소 검증을 통과한다.
    private static final byte[] PNG_1X1 = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D
    };

    @Autowired
    private BusinessClock businessClock;

    // ===== 시딩 (challenge 도메인에 생성 로직이 없어 직접 넣는다) =====

    private long userIdOf(String email) {
        return jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, email);
    }

    private long seedGroup(long ownerId) {
        jdbcTemplate.update(
                "insert into challenge_groups"
                        + " (name, description, category, map_type, visibility, max_members, owner_id, status, created_at, updated_at)"
                        + " values ('스트릭그룹', null, 'DEV', 'STUDY_ROOM', 'PUBLIC', 10, ?, 'ACTIVE', now(6), now(6))",
                ownerId);
        return jdbcTemplate.queryForObject("select id from challenge_groups order by id desc limit 1", Long.class);
    }

    private long seedChallenge(long groupId, LocalDate startDate, String frequencyType, Integer frequencyValue) {
        jdbcTemplate.update(
                "insert into challenges"
                        + " (group_id, seq_no, start_date, end_date, status, frequency_type, frequency_value,"
                        + " days_of_week, daily_check_in_count, required_day_count, group_current_streak,"
                        + " group_best_streak, allow_photo, created_at, updated_at)"
                        + " values (?, 1, ?, ?, 'ACTIVE', ?, ?, null, 1, 30, 0, 0, true, now(6), now(6))",
                groupId,
                startDate,
                startDate.plusDays(30),
                frequencyType,
                frequencyValue);
        return jdbcTemplate.queryForObject("select id from challenges order by id desc limit 1", Long.class);
    }

    // 가입일이 의무일 판정의 바닥이라 created_at 을 직접 지정한다.
    private void seedMember(long challengeId, long userId, LocalDate joinedOn) {
        jdbcTemplate.update(
                "insert into challenge_members"
                        + " (challenge_id, user_id, role, status, current_streak, best_streak, left_at,"
                        + " extension_choice, last_completed_date, created_at, updated_at)"
                        + " values (?, ?, 'MEMBER', 'ACTIVE', 0, 0, null, 'PENDING', null, ?, now(6))",
                challengeId,
                userId,
                joinedOn.atTime(10, 0));
    }

    private long setUpChallenge(long userId, LocalDate startDate, String frequencyType, Integer frequencyValue) {
        long groupId = seedGroup(userId);
        long challengeId = seedChallenge(groupId, startDate, frequencyType, frequencyValue);
        seedMember(challengeId, userId, startDate);
        return challengeId;
    }

    private void seedUserStreak(long userId, int personalStreak, LocalDate lastCheckedInDate) {
        jdbcTemplate.update(
                "update users set personal_streak = ?, best_streak = ?, last_checked_in_date = ? where id = ?",
                personalStreak,
                personalStreak,
                lastCheckedInDate,
                userId);
    }

    // ===== 요청 헬퍼 =====

    private ResultActions submit(long challengeId, String token) throws Exception {
        var media = new MockMultipartFile("media", "shot.png", "image/png", PNG_1X1);
        return mockMvc.perform(multipart("/api/challenges/{challengeId}/check-ins", challengeId)
                .file(media)
                .param("checkInType", "PHOTO")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions getMyProfile(String token) throws Exception {
        return mockMvc.perform(withToken(get("/api/users/me"), token));
    }

    // ===== 테스트 =====

    @Test
    @DisplayName("하루 인증 목표를 채우면 프로필의 스트릭이 오른다")
    void checkInRaisesPersonalStreak() throws Exception {
        Tokens tokens = loginAs(EMAIL, NICKNAME);
        long userId = userIdOf(EMAIL);
        LocalDate today = businessClock.today();
        long challengeId = setUpChallenge(userId, today.minusDays(10), "DAILY", null);

        getMyProfile(tokens.accessToken())
                .andExpect(jsonPath("$.personalStreak").value(0));
        submit(challengeId, tokens.accessToken()).andExpect(status().isCreated());

        getMyProfile(tokens.accessToken())
                .andExpect(jsonPath("$.personalStreak").value(1))
                .andExpect(jsonPath("$.bestStreak").value(1))
                .andExpect(jsonPath("$.lastCheckedInDate").value(today.toString()));
    }

    @Test
    @DisplayName("오늘 시작한 챌린지에서 첫 인증을 해도 기존 스트릭이 리셋되지 않는다")
    void firstCheckInOfBrandNewChallengeKeepsStreak() throws Exception {
        Tokens tokens = loginAs(EMAIL, NICKNAME);
        long userId = userIdOf(EMAIL);
        LocalDate today = businessClock.today();
        seedUserStreak(userId, 10, today.minusDays(1));
        // 오늘 시작한 챌린지라 직전 인증 대상일이 없다.
        long challengeId = setUpChallenge(userId, today, "DAILY", null);

        submit(challengeId, tokens.accessToken()).andExpect(status().isCreated());

        getMyProfile(tokens.accessToken())
                .andExpect(jsonPath("$.personalStreak").value(11));
    }

    @Test
    @DisplayName("대상일이 아닌 날을 건너뛰어도 스트릭이 이어진다")
    void skippingNonRequiredDaysKeepsStreak() throws Exception {
        Tokens tokens = loginAs(EMAIL, NICKNAME);
        long userId = userIdOf(EMAIL);
        LocalDate today = businessClock.today();
        // 3일 주기라 시작일 기준 9일 전, 6일 전, 3일 전, 오늘이 대상일이다.
        long challengeId = setUpChallenge(userId, today.minusDays(9), "EVERY_N_DAYS", 3);
        seedUserStreak(userId, 4, today.minusDays(3));

        submit(challengeId, tokens.accessToken()).andExpect(status().isCreated());

        getMyProfile(tokens.accessToken())
                .andExpect(jsonPath("$.personalStreak").value(5));
    }

    @Test
    @DisplayName("의무일을 빼먹었으면 프로필의 스트릭이 0 으로 보인다")
    void profileShowsZeroAfterMissedRequiredDay() throws Exception {
        Tokens tokens = loginAs(EMAIL, NICKNAME);
        long userId = userIdOf(EMAIL);
        LocalDate today = businessClock.today();
        setUpChallenge(userId, today.minusDays(10), "DAILY", null);
        seedUserStreak(userId, 5, today.minusDays(3));

        // 최고 기록은 남고 현재 스트릭만 0 으로 보인다.
        getMyProfile(tokens.accessToken())
                .andExpect(jsonPath("$.personalStreak").value(0))
                .andExpect(jsonPath("$.bestStreak").value(5));
    }

    @Test
    @DisplayName("속한 챌린지가 없으면 마지막 스트릭이 그대로 보인다")
    void profileKeepsStreakWithoutAnyChallenge() throws Exception {
        Tokens tokens = loginAs(EMAIL, NICKNAME);
        seedUserStreak(userIdOf(EMAIL), 15, businessClock.today().minusMonths(2));

        getMyProfile(tokens.accessToken())
                .andExpect(jsonPath("$.personalStreak").value(15));
    }
}
