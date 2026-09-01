package com.gommit.domain.point;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gommit.domain.point.entity.GroupPointReason;
import com.gommit.domain.point.entity.UserPointReason;
import com.gommit.domain.point.service.PointService;
import com.gommit.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

@DisplayName("포인트 API")
class PointApiIntegrationTest extends IntegrationTestSupport {

    private static final String EMAIL = "gommit@example.com";
    private static final String NICKNAME = "꼬밋러";

    @Autowired
    private PointService pointService;

    private ResultActions getMyBalance(String accessToken) throws Exception {
        return mockMvc.perform(withToken(get("/api/users/me/points"), accessToken));
    }

    private ResultActions getMyHistories(String accessToken, String queryString) throws Exception {
        return mockMvc.perform(withToken(get("/api/users/me/points/histories" + queryString), accessToken));
    }

    private ResultActions getMyHistoryDetail(String accessToken, Long historyId) throws Exception {
        return mockMvc.perform(withToken(get("/api/users/me/points/histories/" + historyId), accessToken));
    }

    private ResultActions getGroupBalance(String accessToken, Long groupId) throws Exception {
        return mockMvc.perform(withToken(get("/api/groups/" + groupId + "/points"), accessToken));
    }

    private ResultActions getGroupHistories(String accessToken, Long groupId) throws Exception {
        return mockMvc.perform(withToken(get("/api/groups/" + groupId + "/points/histories"), accessToken));
    }

    private ResultActions getGroupHistoryDetail(String accessToken, Long groupId, Long historyId) throws Exception {
        return mockMvc.perform(
                withToken(get("/api/groups/" + groupId + "/points/histories/" + historyId), accessToken));
    }

    private Long userIdOf(String email) {
        return jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, email);
    }

    // group_points/group_point_histories 에 challenge_groups FK 가 걸려있어 더미 그룹 행이 필요하다.
    private Long insertTestGroup(Long ownerId) {
        jdbcTemplate.update(
                "insert into challenge_groups"
                        + " (name, category, map_type, visibility, max_members, owner_id, status, created_at, updated_at)"
                        + " values ('테스트 그룹', 'EXERCISE', 'GYM', 'PUBLIC', 6, ?, 'ACTIVE', now(), now())",
                ownerId);
        return jdbcTemplate.queryForObject("select max(id) from challenge_groups", Long.class);
    }

    @Nested
    @DisplayName("개인 포인트 잔액 조회")
    class MyBalance {

        @Test
        @DisplayName("미인증이면 401")
        void requiresAuthentication() throws Exception {
            mockMvc.perform(get("/api/users/me/points"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("포인트 이벤트가 없으면 전부 0이다")
        void returnsZeroWhenNoHistory() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);

            getMyBalance(tokens.accessToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").value(0))
                    .andExpect(jsonPath("$.monthlyEarned").value(0))
                    .andExpect(jsonPath("$.monthlySpent").value(0))
                    .andExpect(jsonPath("$.totalEarned").value(0));
        }

        @Test
        @DisplayName("지급/차감이 반영된 잔액을 반환한다")
        void returnsAccumulatedBalance() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            Long userId = userIdOf(EMAIL);

            pointService.reward(userId, null, 100, UserPointReason.CHECK_IN, "오운완");
            pointService.deduct(userId, 30, UserPointReason.ITEM_PURCHASE, "핑크 왕리본");

            getMyBalance(tokens.accessToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").value(70))
                    .andExpect(jsonPath("$.monthlyEarned").value(100))
                    .andExpect(jsonPath("$.monthlySpent").value(30))
                    .andExpect(jsonPath("$.totalEarned").value(100));
        }
    }

    @Nested
    @DisplayName("개인 포인트 이력 조회")
    class MyHistories {

        @Test
        @DisplayName("미인증이면 401")
        void requiresAuthentication() throws Exception {
            mockMvc.perform(get("/api/users/me/points/histories")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("이력이 없으면 빈 목록에 hasNext=false")
        void returnsEmptyWhenNoHistory() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);

            getMyHistories(tokens.accessToken(), "")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content").isEmpty())
                    .andExpect(jsonPath("$.hasNext").value(false))
                    .andExpect(jsonPath("$.nextCursor").doesNotExist());
        }

        @Test
        @DisplayName("size보다 많으면 잘라서 주고 hasNext=true다")
        void paginatesWithCursor() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            Long userId = userIdOf(EMAIL);

            for (int i = 0; i < 3; i++) {
                pointService.reward(userId, null, 10, UserPointReason.CHECK_IN, "오운완");
            }

            getMyHistories(tokens.accessToken(), "?size=2")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.hasNext").value(true))
                    .andExpect(jsonPath("$.nextCursor").exists());
        }

        @Test
        @DisplayName("reason 필터를 적용하면 해당 사유만 온다")
        void filtersByReason() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            Long userId = userIdOf(EMAIL);

            pointService.reward(userId, null, 40, UserPointReason.CHECK_IN, "오운완");
            pointService.deduct(userId, 10, UserPointReason.ITEM_PURCHASE, "핑크 왕리본");

            getMyHistories(tokens.accessToken(), "?reason=ITEM_PURCHASE")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].reason").value("ITEM_PURCHASE"));
        }
    }

    @Nested
    @DisplayName("개인 포인트 이력 상세 조회")
    class MyHistoryDetail {

        @Test
        @DisplayName("존재하지 않으면 404")
        void returns404WhenNotFound() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);

            getMyHistoryDetail(tokens.accessToken(), 999L)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("POINT_HISTORY_NOT_FOUND"));
        }

        @Test
        @DisplayName("본인 이력이면 상세를 반환한다")
        void returnsOwnHistory() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            Long userId = userIdOf(EMAIL);
            pointService.reward(userId, null, 40, UserPointReason.CHECK_IN, "오운완");
            Long historyId = jdbcTemplate.queryForObject("select max(id) from user_point_histories", Long.class);

            getMyHistoryDetail(tokens.accessToken(), historyId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sourceName").value("오운완"))
                    .andExpect(jsonPath("$.balanceAfter").value(40));
        }

        @Test
        @DisplayName("다른 사용자의 이력이면 존재해도 404다")
        void returns404ForOthersHistory() throws Exception {
            loginAs(EMAIL, NICKNAME);
            Long ownerId = userIdOf(EMAIL);
            pointService.reward(ownerId, null, 40, UserPointReason.CHECK_IN, "오운완");
            Long historyId = jdbcTemplate.queryForObject("select max(id) from user_point_histories", Long.class);

            var otherTokens = loginAs("other@example.com", "다른유저");

            getMyHistoryDetail(otherTokens.accessToken(), historyId)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("POINT_HISTORY_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("그룹 포인트 잔액 조회")
    class GroupBalance {

        @Test
        @DisplayName("미인증이면 401")
        void requiresAuthentication() throws Exception {
            mockMvc.perform(get("/api/groups/1/points")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("포인트 이벤트가 없으면 0이다")
        void returnsZeroWhenNoHistory() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            Long groupId = insertTestGroup(userIdOf(EMAIL));

            getGroupBalance(tokens.accessToken(), groupId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.groupId").value(groupId))
                    .andExpect(jsonPath("$.balance").value(0));
        }

        @Test
        @DisplayName("지급/차감이 반영된 잔액을 반환한다")
        void returnsAccumulatedBalance() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            Long groupId = insertTestGroup(userIdOf(EMAIL));

            pointService.rewardGroup(groupId, 500, GroupPointReason.DAILY_ALL_COMPLETE, "오운완");
            pointService.deductGroup(groupId, 200, GroupPointReason.BACKGROUND_PURCHASE, "루프탑 운동장");

            getGroupBalance(tokens.accessToken(), groupId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").value(300));
        }
    }

    @Nested
    @DisplayName("그룹 포인트 이력 조회")
    class GroupHistories {

        @Test
        @DisplayName("이력이 없으면 빈 목록이다")
        void returnsEmptyWhenNoHistory() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            Long groupId = insertTestGroup(userIdOf(EMAIL));

            getGroupHistories(tokens.accessToken(), groupId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty())
                    .andExpect(jsonPath("$.hasNext").value(false));
        }

        @Test
        @DisplayName("최근 발생 순서로 반환한다")
        void returnsMostRecentFirst() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            Long groupId = insertTestGroup(userIdOf(EMAIL));

            pointService.rewardGroup(groupId, 100, GroupPointReason.DAILY_ALL_COMPLETE, "1번째");
            pointService.rewardGroup(groupId, 200, GroupPointReason.DAILY_ALL_COMPLETE, "2번째");

            getGroupHistories(tokens.accessToken(), groupId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].sourceName").value("2번째"))
                    .andExpect(jsonPath("$.content[1].sourceName").value("1번째"));
        }
    }

    @Nested
    @DisplayName("그룹 포인트 이력 상세 조회")
    class GroupHistoryDetail {

        @Test
        @DisplayName("존재하지 않으면 404")
        void returns404WhenNotFound() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            Long groupId = insertTestGroup(userIdOf(EMAIL));

            getGroupHistoryDetail(tokens.accessToken(), groupId, 999L)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("POINT_HISTORY_NOT_FOUND"));
        }

        @Test
        @DisplayName("다른 그룹의 이력이면 존재해도 404다")
        void returns404ForOtherGroupsHistory() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            Long groupId = insertTestGroup(userIdOf(EMAIL));
            Long otherGroupId = insertTestGroup(userIdOf(EMAIL));

            pointService.rewardGroup(groupId, 100, GroupPointReason.DAILY_ALL_COMPLETE, "오운완");
            Long historyId = jdbcTemplate.queryForObject("select max(id) from group_point_histories", Long.class);

            getGroupHistoryDetail(tokens.accessToken(), otherGroupId, historyId)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("POINT_HISTORY_NOT_FOUND"));
        }
    }
}
