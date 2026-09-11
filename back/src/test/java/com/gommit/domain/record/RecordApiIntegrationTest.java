package com.gommit.domain.record;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gommit.support.IntegrationTestSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

// PersonalRecordController/GroupRecordController 컨트롤러 레벨 통합 테스트.
// 머지 "생성" API가 없어서(RecordBatchService는 스텁), 그룹/챌린지는 실제 API로 만들고
// 머지 데이터는 GroupApiIntegrationTest/RecordQueryServiceTest와 같은 방식으로 SQL로 직접 넣는다.
@DisplayName("Record API")
class RecordApiIntegrationTest extends IntegrationTestSupport {

    private static final String EMAIL = "gommit@example.com";
    private static final String NICKNAME = "꼬밋러";

    private ResultActions createGroup(String accessToken) throws Exception {
        String body = """
                {
                  "name": "테스트 그룹",
                  "description": "레코드 테스트용 그룹",
                  "category": "EXERCISE",
                  "mapType": "GYM",
                  "visibility": "PUBLIC",
                  "maxMembers": 6,
                  "challenge": {
                    "startDate": "%s",
                    "endDate": "%s",
                    "frequencyType": "DAILY",
                    "frequencyValue": null,
                    "daysOfWeek": null,
                    "dailyCheckInCount": 1,
                    "allowedTypes": ["PHOTO"]
                  }
                }
                """.formatted(LocalDate.now().plusDays(1), LocalDate.now().plusDays(30));
        return mockMvc.perform(jsonRequest(withToken(post("/api/groups"), accessToken), body));
    }

    private Long userIdOf(String email) {
        return jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, email);
    }

    private Long groupIdOf(Long ownerId) {
        return jdbcTemplate.queryForObject(
                "select max(id) from challenge_groups where owner_id = ?", Long.class, ownerId);
    }

    private Long challengeIdOf(Long groupId) {
        return jdbcTemplate.queryForObject("select id from challenges where group_id = ?", Long.class, groupId);
    }

    // 실제 그룹 생성 API로 그룹+READY 챌린지+owner를 ACTIVE 멤버로 만들고 challengeId를 반환한다.
    private Long createChallengeViaApi(String accessToken, Long ownerId) throws Exception {
        createGroup(accessToken).andExpect(status().isCreated());
        Long groupId = groupIdOf(ownerId);
        return challengeIdOf(groupId);
    }

    private Long insertMonthlyMerge(Long challengeId, int seqNo, int totalDays, int avgRate) {
        LocalDate start = LocalDate.now().minusDays(totalDays);
        LocalDate end = start.plusDays(totalDays - 1);
        jdbcTemplate.update(
                "insert into monthly_merges"
                        + " (challenge_id, seq_no, period_start, period_end, total_days, total_check_in_count,"
                        + " average_completion_rate, published_at, created_at, updated_at)"
                        + " values (?, ?, ?, ?, ?, ?, ?, now(), now(), now())",
                challengeId,
                seqNo,
                start,
                end,
                totalDays,
                totalDays * avgRate / 100,
                avgRate);
        return jdbcTemplate.queryForObject(
                "select id from monthly_merges where challenge_id = ? and seq_no = ?", Long.class, challengeId, seqNo);
    }

    private void insertMonthlyMergeResult(Long monthlyMergeId, Long userId, int completedDayCount) {
        jdbcTemplate.update(
                "insert into monthly_merge_results"
                        + " (monthly_merge_id, user_id, ranking, completion_rate, completed_day_count,"
                        + " total_check_in_count, best_streak_in_period, earned_points, contribution_rate,"
                        + " created_at, updated_at)"
                        + " values (?, ?, 1, 90, ?, ?, 5, 1000, 100, now(), now())",
                monthlyMergeId,
                userId,
                completedDayCount,
                completedDayCount);
    }

    private Long insertFinalMerge(Long challengeId, int totalDays, int avgRate) {
        LocalDate start = LocalDate.now().minusDays(totalDays);
        LocalDate end = start.plusDays(totalDays - 1);
        jdbcTemplate.update(
                "insert into final_merges"
                        + " (challenge_id, period_start, period_end, total_days, total_check_in_count,"
                        + " average_completion_rate, published_at, created_at, updated_at)"
                        + " values (?, ?, ?, ?, ?, ?, now(), now(), now())",
                challengeId,
                start,
                end,
                totalDays,
                totalDays * avgRate / 100,
                avgRate);
        return jdbcTemplate.queryForObject(
                "select id from final_merges where challenge_id = ?", Long.class, challengeId);
    }

    private void insertFinalMergeResult(Long finalMergeId, Long userId, int completedDayCount) {
        jdbcTemplate.update(
                "insert into final_merge_results"
                        + " (final_merge_id, user_id, ranking, completion_rate, completed_day_count,"
                        + " total_check_in_count, best_streak_in_period, earned_points, contribution_rate,"
                        + " created_at, updated_at)"
                        + " values (?, ?, 1, 90, ?, ?, 5, 1000, 100, now(), now())",
                finalMergeId,
                userId,
                completedDayCount,
                completedDayCount);
    }

    @Nested
    @DisplayName("내 개인 전체 통계 조회")
    class GetMyStats {

        @Test
        @DisplayName("미인증이면 401")
        void requiresAuthentication() throws Exception {
            mockMvc.perform(get("/api/users/me/stats")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("참여한 머지가 없으면 전부 0/빈 값이다")
        void returnsEmptyWhenNoParticipation() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);

            mockMvc.perform(withToken(get("/api/users/me/stats"), tokens.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.summary.totalCheckInCount").value(0))
                    .andExpect(jsonPath("$.monthlyTrend").isEmpty());
        }
    }

    @Nested
    @DisplayName("내가 속한 챌린지별 머지 진행 현황")
    class GetMyChallengeMergeOverviews {

        @Test
        @DisplayName("미인증이면 401")
        void requiresAuthentication() throws Exception {
            mockMvc.perform(get("/api/users/me/challenge-merge-overviews")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("그룹을 만들면 그 챌린지가 목록에 나온다")
        void returnsMyChallenges() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            Long ownerId = userIdOf(EMAIL);
            createChallengeViaApi(tokens.accessToken(), ownerId);

            mockMvc.perform(withToken(get("/api/users/me/challenge-merge-overviews"), tokens.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1));
        }
    }

    @Nested
    @DisplayName("내 월간 머지 아카이브 조회")
    class GetMyMonthlyMergeArchive {

        @Test
        @DisplayName("미인증이면 401")
        void requiresAuthentication() throws Exception {
            mockMvc.perform(get("/api/users/me/monthly-merges")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("이력이 없으면 빈 목록이다")
        void returnsEmptyWhenNoHistory() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);

            mockMvc.perform(withToken(get("/api/users/me/monthly-merges"), tokens.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty())
                    .andExpect(jsonPath("$.hasNext").value(false));
        }
    }

    @Nested
    @DisplayName("머지 진행 현황 조회")
    class GetChallengeMergeOverview {

        @Test
        @DisplayName("미인증이면 401")
        void requiresAuthentication() throws Exception {
            mockMvc.perform(get("/api/challenges/1/merge-overview")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("그 챌린지의 멤버가 아니면 403")
        void returns403WhenNotMember() throws Exception {
            var ownerTokens = loginAs(EMAIL, NICKNAME);
            Long challengeId = createChallengeViaApi(ownerTokens.accessToken(), userIdOf(EMAIL));
            var otherTokens = loginAs("other@example.com", "다른유저");

            mockMvc.perform(withToken(
                            get("/api/challenges/" + challengeId + "/merge-overview"), otherTokens.accessToken()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        @Test
        @DisplayName("멤버면 진행 현황을 반환한다")
        void returnsOverviewForMember() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            Long challengeId = createChallengeViaApi(tokens.accessToken(), userIdOf(EMAIL));

            mockMvc.perform(withToken(get("/api/challenges/" + challengeId + "/merge-overview"), tokens.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.challengeId").value(challengeId))
                    .andExpect(jsonPath("$.completedMergeCount").value(0));
        }
    }

    @Nested
    @DisplayName("머지 목록 조회")
    class GetMergeList {

        @Test
        @DisplayName("미인증이면 401")
        void requiresAuthentication() throws Exception {
            mockMvc.perform(get("/api/challenges/1/merges")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("그 챌린지의 멤버가 아니면 403")
        void returns403WhenNotMember() throws Exception {
            var ownerTokens = loginAs(EMAIL, NICKNAME);
            Long challengeId = createChallengeViaApi(ownerTokens.accessToken(), userIdOf(EMAIL));
            var otherTokens = loginAs("other@example.com", "다른유저");

            mockMvc.perform(withToken(get("/api/challenges/" + challengeId + "/merges"), otherTokens.accessToken()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        @Test
        @DisplayName("발행된 월간/최종 머지를 최신순으로 반환한다")
        void returnsMergesForMember() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            Long userId = userIdOf(EMAIL);
            Long challengeId = createChallengeViaApi(tokens.accessToken(), userId);
            insertMonthlyMerge(challengeId, 1, 30, 90);
            insertFinalMerge(challengeId, 60, 85);

            mockMvc.perform(withToken(get("/api/challenges/" + challengeId + "/merges"), tokens.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].type").value("FINAL"));
        }
    }

    @Nested
    @DisplayName("월간 머지 상세 조회")
    class GetMonthlyMergeDetail {

        @Test
        @DisplayName("미인증이면 401")
        void requiresAuthentication() throws Exception {
            mockMvc.perform(get("/api/challenges/1/monthly-merges/1")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("그 챌린지의 멤버가 아니면 403")
        void returns403WhenNotMember() throws Exception {
            var ownerTokens = loginAs(EMAIL, NICKNAME);
            Long challengeId = createChallengeViaApi(ownerTokens.accessToken(), userIdOf(EMAIL));
            insertMonthlyMerge(challengeId, 1, 30, 90);
            var otherTokens = loginAs("other@example.com", "다른유저");

            mockMvc.perform(withToken(
                            get("/api/challenges/" + challengeId + "/monthly-merges/1"), otherTokens.accessToken()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        @Test
        @DisplayName("존재하지 않는 회차면 404")
        void returns404WhenNotFound() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            Long challengeId = createChallengeViaApi(tokens.accessToken(), userIdOf(EMAIL));

            mockMvc.perform(withToken(
                            get("/api/challenges/" + challengeId + "/monthly-merges/1"), tokens.accessToken()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("MONTHLY_MERGE_NOT_FOUND"));
        }

        @Test
        @DisplayName("멤버면 참여자 목록과 함께 상세를 반환한다")
        void returnsDetailForMember() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            Long userId = userIdOf(EMAIL);
            Long challengeId = createChallengeViaApi(tokens.accessToken(), userId);
            Long monthlyMergeId = insertMonthlyMerge(challengeId, 1, 30, 90);
            insertMonthlyMergeResult(monthlyMergeId, userId, 27);

            mockMvc.perform(withToken(
                            get("/api/challenges/" + challengeId + "/monthly-merges/1"), tokens.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.seqNo").value(1))
                    .andExpect(jsonPath("$.participants.length()").value(1))
                    .andExpect(jsonPath("$.participants[0].completedDayCount").value(27));
        }
    }

    @Nested
    @DisplayName("최종 머지 상세 조회")
    class GetFinalMergeDetail {

        @Test
        @DisplayName("미인증이면 401")
        void requiresAuthentication() throws Exception {
            mockMvc.perform(get("/api/challenges/1/final-merge")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("그 챌린지의 멤버가 아니면 403")
        void returns403WhenNotMember() throws Exception {
            var ownerTokens = loginAs(EMAIL, NICKNAME);
            Long challengeId = createChallengeViaApi(ownerTokens.accessToken(), userIdOf(EMAIL));
            insertFinalMerge(challengeId, 60, 85);
            var otherTokens = loginAs("other@example.com", "다른유저");

            mockMvc.perform(withToken(
                            get("/api/challenges/" + challengeId + "/final-merge"), otherTokens.accessToken()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        @Test
        @DisplayName("존재하지 않으면 404")
        void returns404WhenNotFound() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            Long challengeId = createChallengeViaApi(tokens.accessToken(), userIdOf(EMAIL));

            mockMvc.perform(withToken(get("/api/challenges/" + challengeId + "/final-merge"), tokens.accessToken()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("FINAL_MERGE_NOT_FOUND"));
        }

        @Test
        @DisplayName("멤버면 참여자 목록과 함께 상세를 반환한다")
        void returnsDetailForMember() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            Long userId = userIdOf(EMAIL);
            Long challengeId = createChallengeViaApi(tokens.accessToken(), userId);
            Long finalMergeId = insertFinalMerge(challengeId, 60, 85);
            insertFinalMergeResult(finalMergeId, userId, 51);

            mockMvc.perform(withToken(get("/api/challenges/" + challengeId + "/final-merge"), tokens.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalDays").value(60))
                    .andExpect(jsonPath("$.participants[0].completedDayCount").value(51));
        }
    }
}
