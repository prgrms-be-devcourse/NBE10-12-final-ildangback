package com.gommit.support;

import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;

// challenge/group/challenge_members 를 직접 INSERT 하는 시딩 헬퍼(challenge 도메인에 생성 로직이
// 없어 테스트가 직접 넣는다). IntegrationTestSupport 를 상속하는 여러 최상위 테스트 클래스가 똑같은
// 시딩을 필요로 하는데, Java 는 클래스 하나가 IntegrationTestSupport 와 또 다른 기반 클래스를 동시에
// 상속할 수 없어 정적 유틸로 뽑았다 — 상속 대신 jdbcTemplate 을 인자로 받는다.
public final class ChallengeFixtures {

    private ChallengeFixtures() {}

    public static long userIdOf(JdbcTemplate jdbcTemplate, String email) {
        return jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, email);
    }

    public static long seedGroup(JdbcTemplate jdbcTemplate, long ownerId) {
        jdbcTemplate.update(
                "insert into challenge_groups"
                        + " (name, description, category, map_type, visibility, max_members, owner_id, status, created_at, updated_at)"
                        + " values ('테스트그룹', null, 'DEV', 'STUDY_ROOM', 'PUBLIC', 10, ?, 'ACTIVE', now(6), now(6))",
                ownerId);
        return jdbcTemplate.queryForObject("select id from challenge_groups order by id desc limit 1", Long.class);
    }

    public static long seedChallenge(
            JdbcTemplate jdbcTemplate, long groupId, int dailyCheckInCount, String challengeStatus) {
        jdbcTemplate.update(
                "insert into challenges"
                        + " (group_id, seq_no, start_date, end_date, status, frequency_type, frequency_value,"
                        + " days_of_week, daily_check_in_count, required_day_count, group_current_streak,"
                        + " group_best_streak, allow_photo, created_at, updated_at)"
                        + " values (?, 1, ?, ?, ?, 'DAILY', null, null, ?, 30, 0, 0, true, now(6), now(6))",
                groupId,
                LocalDate.now().minusDays(10),
                LocalDate.now().plusDays(30),
                challengeStatus,
                dailyCheckInCount);
        return jdbcTemplate.queryForObject("select id from challenges order by id desc limit 1", Long.class);
    }

    public static void seedMember(
            JdbcTemplate jdbcTemplate, long challengeId, long userId, String memberStatus, LocalDate leftOn) {
        jdbcTemplate.update(
                "insert into challenge_members"
                        + " (challenge_id, user_id, role, status, current_streak, best_streak, left_at,"
                        + " extension_choice, created_at, updated_at)"
                        + " values (?, ?, 'MEMBER', ?, 0, 0, ?, 'PENDING', now(6), now(6))",
                challengeId,
                userId,
                memberStatus,
                leftOn == null ? null : leftOn.atStartOfDay());
    }

    public static long setUpChallenge(JdbcTemplate jdbcTemplate, String memberEmail, int dailyCheckInCount) {
        long userId = userIdOf(jdbcTemplate, memberEmail);
        long groupId = seedGroup(jdbcTemplate, userId);
        long challengeId = seedChallenge(jdbcTemplate, groupId, dailyCheckInCount, "ACTIVE");
        seedMember(jdbcTemplate, challengeId, userId, "ACTIVE", null);
        return challengeId;
    }
}
