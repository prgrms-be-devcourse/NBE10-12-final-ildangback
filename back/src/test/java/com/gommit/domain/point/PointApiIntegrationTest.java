package com.gommit.domain.point;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gommit.domain.point.entity.GroupPointReason;
import com.gommit.domain.point.entity.UserPointReason;
import com.gommit.domain.point.service.GroupPointService;
import com.gommit.domain.point.service.PersonalPointService;
import com.gommit.support.IntegrationTestSupport;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
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
    private PersonalPointService personalPointService;

    @Autowired
    private GroupPointService groupPointService;

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
        Long groupId = jdbcTemplate.queryForObject("select max(id) from challenge_groups", Long.class);
        // 그룹 포인트 조회 API는 멤버십을 검증하므로 owner를 ACTIVE 멤버로도 넣어준다.
        jdbcTemplate.update(
                "insert into group_members (group_id, user_id, status, created_at, updated_at)"
                        + " values (?, ?, 'ACTIVE', now(), now())",
                groupId,
                ownerId);
        return groupId;
    }

    // insertTestGroup(SQL 직접 삽입)과 달리, 실제 그룹 생성/가입 API를 그대로 태워서
    // "그룹이 실제로 어떻게 인증되는지"(생성 시 owner 자동 가입, /members로 가입)를 검증한다.
    private ResultActions createGroupViaApi(String accessToken) throws Exception {
        String body = """
                {
                  "name": "테스트 그룹",
                  "description": "실제 API로 만든 그룹",
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
                """.formatted(LocalDate.now().plusDays(1), LocalDate.now().plusDays(7));
        return mockMvc.perform(jsonRequest(withToken(post("/api/groups"), accessToken), body));
    }

    private ResultActions joinGroupViaApi(String accessToken, Long groupId) throws Exception {
        return mockMvc.perform(withToken(post("/api/groups/" + groupId + "/members"), accessToken));
    }

    @Nested
    @DisplayName("실제 그룹 생성/가입 API로 확인하는 그룹 포인트 멤버십")
    class GroupPointMembershipFlow {

        @Test
        @DisplayName("그룹을 만들면 owner가 자동으로 멤버가 돼서 바로 포인트 조회가 되고, 나중에 가입한 멤버도 조회된다")
        void ownerAndJoinedMemberCanReadGroupPoints() throws Exception {
            var ownerTokens = loginAs(EMAIL, NICKNAME);
            Long ownerId = userIdOf(EMAIL);
            createGroupViaApi(ownerTokens.accessToken()).andExpect(status().isCreated());
            Long groupId = jdbcTemplate.queryForObject(
                    "select max(id) from challenge_groups where owner_id = ?", Long.class, ownerId);

            // 그룹 생성 직후 - 별도 가입 절차 없이 owner가 이미 멤버라서 바로 조회된다.
            getGroupBalance(ownerTokens.accessToken(), groupId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").value(0));

            // 아직 안 들어간 다른 유저는 403.
            var otherTokens = loginAs("other@example.com", "다른유저");
            getGroupBalance(otherTokens.accessToken(), groupId)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NOT_GROUP_MEMBER"));

            // /members로 실제 가입하면 그때부터 조회된다.
            joinGroupViaApi(otherTokens.accessToken(), groupId).andExpect(status().isCreated());
            getGroupBalance(otherTokens.accessToken(), groupId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").value(0));
        }
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

            personalPointService.reward(userId, null, 100, UserPointReason.CHECK_IN, "오운완");
            personalPointService.deduct(userId, 30, UserPointReason.ITEM_PURCHASE, "핑크 왕리본");

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
                personalPointService.reward(userId, null, 10, UserPointReason.CHECK_IN, "오운완");
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

            personalPointService.reward(userId, null, 40, UserPointReason.CHECK_IN, "오운완");
            personalPointService.deduct(userId, 10, UserPointReason.ITEM_PURCHASE, "핑크 왕리본");

            getMyHistories(tokens.accessToken(), "?reason=ITEM_PURCHASE")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].reason").value("ITEM_PURCHASE"));
        }

        // from만/to만 있을 때 실제 DB(MySQL)에서 null 파라미터가 예외 없이 처리되는지 확인한다.
        // 유닛 테스트는 리포지토리를 mock으로 대체해서 JPQL이 실제로 null-safe한지는 증명 못 한다.
        @Test
        @DisplayName("from만 있으면 400")
        void rejectsFromOnly() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);

            getMyHistories(tokens.accessToken(), "?from=2020-01-01")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
        }

        @Test
        @DisplayName("to만 있으면 400")
        void rejectsToOnly() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);

            getMyHistories(tokens.accessToken(), "?to=2099-12-31")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
        }

        @Test
        @DisplayName("from/to를 함께 주면 그 범위만 조회된다")
        void filtersByFromAndTo() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            Long userId = userIdOf(EMAIL);
            personalPointService.reward(userId, null, 40, UserPointReason.CHECK_IN, "오운완");

            getMyHistories(tokens.accessToken(), "?from=2020-01-01&to=2099-12-31")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1));
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
            personalPointService.reward(userId, null, 40, UserPointReason.CHECK_IN, "오운완");
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
            personalPointService.reward(ownerId, null, 40, UserPointReason.CHECK_IN, "오운완");
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

            groupPointService.reward(groupId, 500, GroupPointReason.DAILY_ALL_COMPLETE, "오운완");
            groupPointService.deduct(groupId, 200, GroupPointReason.BACKGROUND_PURCHASE, "루프탑 운동장");

            getGroupBalance(tokens.accessToken(), groupId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").value(300));
        }

        @Test
        @DisplayName("그룹 멤버가 아니면 403")
        void returns403WhenNotMember() throws Exception {
            loginAs(EMAIL, NICKNAME);
            Long groupId = insertTestGroup(userIdOf(EMAIL));
            var otherTokens = loginAs("other@example.com", "다른유저");

            getGroupBalance(otherTokens.accessToken(), groupId)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NOT_GROUP_MEMBER"));
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

            groupPointService.reward(groupId, 100, GroupPointReason.DAILY_ALL_COMPLETE, "1번째");
            groupPointService.reward(groupId, 200, GroupPointReason.DAILY_ALL_COMPLETE, "2번째");

            getGroupHistories(tokens.accessToken(), groupId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].sourceName").value("2번째"))
                    .andExpect(jsonPath("$.content[1].sourceName").value("1번째"));
        }

        @Test
        @DisplayName("그룹 멤버가 아니면 403")
        void returns403WhenNotMember() throws Exception {
            loginAs(EMAIL, NICKNAME);
            Long groupId = insertTestGroup(userIdOf(EMAIL));
            var otherTokens = loginAs("other@example.com", "다른유저");

            getGroupHistories(otherTokens.accessToken(), groupId)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NOT_GROUP_MEMBER"));
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
        @DisplayName("그룹의 이력이면 상세를 반환한다")
        void returnsHistory() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            Long groupId = insertTestGroup(userIdOf(EMAIL));

            groupPointService.reward(groupId, 100, GroupPointReason.DAILY_ALL_COMPLETE, "오운완");
            Long historyId = jdbcTemplate.queryForObject("select max(id) from group_point_histories", Long.class);

            getGroupHistoryDetail(tokens.accessToken(), groupId, historyId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sourceName").value("오운완"))
                    .andExpect(jsonPath("$.balanceAfter").value(100));
        }

        @Test
        @DisplayName("다른 그룹의 이력이면 존재해도 404다")
        void returns404ForOtherGroupsHistory() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            Long groupId = insertTestGroup(userIdOf(EMAIL));
            Long otherGroupId = insertTestGroup(userIdOf(EMAIL));

            groupPointService.reward(groupId, 100, GroupPointReason.DAILY_ALL_COMPLETE, "오운완");
            Long historyId = jdbcTemplate.queryForObject("select max(id) from group_point_histories", Long.class);

            getGroupHistoryDetail(tokens.accessToken(), otherGroupId, historyId)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("POINT_HISTORY_NOT_FOUND"));
        }

        @Test
        @DisplayName("그룹 멤버가 아니면 403")
        void returns403WhenNotMember() throws Exception {
            loginAs(EMAIL, NICKNAME);
            Long groupId = insertTestGroup(userIdOf(EMAIL));
            groupPointService.reward(groupId, 100, GroupPointReason.DAILY_ALL_COMPLETE, "오운완");
            Long historyId = jdbcTemplate.queryForObject("select max(id) from group_point_histories", Long.class);
            var otherTokens = loginAs("other@example.com", "다른유저");

            getGroupHistoryDetail(otherTokens.accessToken(), groupId, historyId)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NOT_GROUP_MEMBER"));
        }
    }

    @Nested
    @DisplayName("동시 지급/차감 - 잔액 유실 여부")
    class Concurrency {

        // 잔액 행 생성 경쟁(ON DUPLICATE KEY UPDATE 멱등 처리)에 대한 회귀 테스트.
        @Test
        @DisplayName("동시에 N번 지급하면 잔액과 이력이 정확히 N번만큼 쌓인다")
        void concurrentRewardsDoNotLoseUpdates() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            Long userId = userIdOf(EMAIL);
            int threadCount = 8;
            int amountEach = 100;

            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            List<Callable<Void>> tasks = IntStream.range(0, threadCount)
                    .<Callable<Void>>mapToObj(i -> () -> {
                        personalPointService.reward(userId, null, amountEach, UserPointReason.CHECK_IN, "동시성 테스트");
                        return null;
                    })
                    .toList();
            List<Future<Void>> futures = pool.invokeAll(tasks);
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
            for (Future<Void> future : futures) {
                future.get(); // 스레드 내부 예외가 있었다면 여기서 드러난다
            }

            int balance = jdbcTemplate.queryForObject(
                    "select balance from user_points where user_id = ?", Integer.class, userId);
            Integer historyCount = jdbcTemplate.queryForObject(
                    "select count(*) from user_point_histories where user_id = ?", Integer.class, userId);

            assertThat(balance).isEqualTo(threadCount * amountEach);
            assertThat(historyCount).isEqualTo(threadCount);
        }

        // 그룹 포인트도 잔액 테이블/락 구조가 동일하므로 같은 시나리오를 검증한다.
        @Test
        @DisplayName("동시에 N번 그룹에 지급하면 잔액과 이력이 정확히 N번만큼 쌓인다")
        void concurrentGroupRewardsDoNotLoseUpdates() throws Exception {
            loginAs(EMAIL, NICKNAME);
            Long groupId = insertTestGroup(userIdOf(EMAIL));
            int threadCount = 8;
            int amountEach = 50;

            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            List<Callable<Void>> tasks = IntStream.range(0, threadCount)
                    .<Callable<Void>>mapToObj(i -> () -> {
                        groupPointService.reward(groupId, amountEach, GroupPointReason.DAILY_ALL_COMPLETE, "동시성 테스트");
                        return null;
                    })
                    .toList();
            List<Future<Void>> futures = pool.invokeAll(tasks);
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
            for (Future<Void> future : futures) {
                future.get();
            }

            int balance = jdbcTemplate.queryForObject(
                    "select balance from group_points where group_id = ?", Integer.class, groupId);
            Integer historyCount = jdbcTemplate.queryForObject(
                    "select count(*) from group_point_histories where group_id = ?", Integer.class, groupId);

            assertThat(balance).isEqualTo(threadCount * amountEach);
            assertThat(historyCount).isEqualTo(threadCount);
        }
    }
}
