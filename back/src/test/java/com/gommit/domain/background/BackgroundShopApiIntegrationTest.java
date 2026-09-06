package com.gommit.domain.background;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gommit.support.IntegrationTestSupport;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

@DisplayName("그룹 배경 상점 API")
class BackgroundShopApiIntegrationTest extends IntegrationTestSupport {

    private static final String OWNER_EMAIL = "owner@example.com";
    private static final String OWNER_NICKNAME = "방장";

    // ===== 엔드포인트 호출 =====

    private ResultActions getShop(String accessToken, Long groupId) throws Exception {
        return mockMvc.perform(withToken(get("/api/groups/" + groupId + "/shop/backgrounds"), accessToken));
    }

    private ResultActions getShop(String accessToken, Long groupId, boolean owned) throws Exception {
        return mockMvc.perform(
                withToken(get("/api/groups/" + groupId + "/shop/backgrounds?owned=" + owned), accessToken));
    }

    private ResultActions createRequest(String accessToken, Long groupId, Long backgroundId) throws Exception {
        return mockMvc.perform(jsonRequest(
                withToken(post("/api/groups/" + groupId + "/shop/purchase-requests"), accessToken),
                "{\"backgroundId\": " + backgroundId + "}"));
    }

    private ResultActions getCurrentRequest(String accessToken, Long groupId) throws Exception {
        return mockMvc.perform(
                withToken(get("/api/groups/" + groupId + "/shop/purchase-requests/current"), accessToken));
    }

    private ResultActions vote(String accessToken, Long groupId, Long requestId, boolean agreed) throws Exception {
        return mockMvc.perform(jsonRequest(
                withToken(
                        post("/api/groups/" + groupId + "/shop/purchase-requests/" + requestId + "/votes"),
                        accessToken),
                "{\"agreed\": " + agreed + "}"));
    }

    private ResultActions cancelRequest(String accessToken, Long groupId, Long requestId) throws Exception {
        return mockMvc.perform(
                withToken(delete("/api/groups/" + groupId + "/shop/purchase-requests/" + requestId), accessToken));
    }

    private ResultActions applyBackground(String accessToken, Long groupId, Long backgroundId) throws Exception {
        return mockMvc.perform(jsonRequest(
                withToken(patch("/api/groups/" + groupId + "/background"), accessToken),
                "{\"backgroundId\": " + backgroundId + "}"));
    }

    private ResultActions getActiveBackground(String accessToken, Long groupId) throws Exception {
        return mockMvc.perform(withToken(get("/api/groups/" + groupId + "/background"), accessToken));
    }

    private ResultActions leaveGroup(String accessToken, Long groupId) throws Exception {
        return mockMvc.perform(withToken(delete("/api/groups/" + groupId + "/members/me"), accessToken));
    }

    // ===== 준비 =====

    // 판매 배경은 시드로 직접 넣는 방침이라 테스트가 자기 배경을 만든다.
    private Long insertBackground(String mapType, String name, int price) {
        jdbcTemplate.update(
                "insert into backgrounds (map_type, name, image_key, price, created_at, updated_at)"
                        + " values (?, ?, ?, ?, now(6), now(6))",
                mapType,
                name,
                "backgrounds/" + name + ".png",
                price);
        return jdbcTemplate.queryForObject("select max(id) from backgrounds", Long.class);
    }

    private void giveGroupPoints(Long groupId, int amount) {
        jdbcTemplate.update(
                "insert into group_points (group_id, balance, created_at, updated_at)"
                        + " values (?, ?, now(6), now(6))"
                        + " on duplicate key update balance = ?",
                groupId,
                amount,
                amount);
    }

    private Long createGroup(String accessToken) throws Exception {
        String body = """
                {
                  "name": "운동 그룹",
                  "description": "함께 운동하는 그룹",
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

        mockMvc.perform(jsonRequest(withToken(post("/api/groups"), accessToken), body))
                .andExpect(status().isCreated());
        return jdbcTemplate.queryForObject("select max(id) from challenge_groups", Long.class);
    }

    // 방장 1명 + members 명의 참여자로 그룹을 만든다. 반환값 0번이 방장 토큰이다.
    private List<String> createGroupWith(int members, Long[] groupIdHolder) throws Exception {
        List<String> tokens = new ArrayList<>();
        String ownerToken = loginAs(OWNER_EMAIL, OWNER_NICKNAME).accessToken();
        tokens.add(ownerToken);

        Long groupId = createGroup(ownerToken);
        groupIdHolder[0] = groupId;

        for (int i = 0; i < members; i++) {
            String token = loginAs("member" + i + "@example.com", "멤버" + i).accessToken();
            mockMvc.perform(withToken(post("/api/groups/" + groupId + "/members"), token))
                    .andExpect(status().isCreated());
            tokens.add(token);
        }
        return tokens;
    }

    private String requestStatusOf(Long requestId) {
        return jdbcTemplate.queryForObject(
                "select status from background_purchase_requests where id = ?", String.class, requestId);
    }

    private int groupBalanceOf(Long groupId) {
        Integer balance = jdbcTemplate.queryForObject(
                "select balance from group_points where group_id = ?", Integer.class, groupId);
        return balance == null ? 0 : balance;
    }

    private int ownedCountOf(Long groupId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from group_backgrounds where group_id = ?", Integer.class, groupId);
        return count == null ? 0 : count;
    }

    private Long requestIdOf(Long groupId) {
        return jdbcTemplate.queryForObject(
                "select max(id) from background_purchase_requests where group_id = ?", Long.class, groupId);
    }

    @Nested
    @DisplayName("상점 조회")
    class GetShop {

        @Test
        @DisplayName("그룹의 맵 타입 계열 배경만 보인다")
        void onlyMatchingMapTypeIsListed() throws Exception {
            Long[] holder = new Long[1];
            String token = createGroupWith(0, holder).get(0);
            insertBackground("GYM", "헬스장", 100);
            insertBackground("STUDY_ROOM", "독서실", 100);

            getShop(token, holder[0])
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].name").value("헬스장"));
        }

        @Test
        @DisplayName("owned=true 는 보유한 배경만, owned=false 는 아직 안 산 배경만 준다")
        void ownedFilterSplitsCatalog() throws Exception {
            Long[] holder = new Long[1];
            String token = createGroupWith(0, holder).get(0);
            Long bought = insertBackground("GYM", "헬스장", 100);
            insertBackground("GYM", "요가원", 100);
            insertBackground("GYM", "복싱장", 100);
            giveGroupPoints(holder[0], 500);

            // 1인 그룹이라 제안 즉시 구매된다
            createRequest(token, holder[0], bought).andExpect(status().isCreated());

            getShop(token, holder[0])
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(3));

            getShop(token, holder[0], true)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].name").value("헬스장"))
                    .andExpect(jsonPath("$.content[0].owned").value(true));

            getShop(token, holder[0], false)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].name").value("요가원"))
                    .andExpect(jsonPath("$.content[1].name").value("복싱장"));
        }

        @Test
        @DisplayName("아무것도 안 산 그룹은 owned=true 가 비고 owned=false 가 전체다")
        void ownedFilterWithNothingPurchased() throws Exception {
            Long[] holder = new Long[1];
            String token = createGroupWith(0, holder).get(0);
            insertBackground("GYM", "헬스장", 100);
            insertBackground("GYM", "요가원", 100);

            getShop(token, holder[0], true)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(0));

            getShop(token, holder[0], false)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2));
        }

        @Test
        @DisplayName("필터는 다른 계열 배경을 넘기지 않는다")
        void ownedFilterKeepsMapTypeScope() throws Exception {
            Long[] holder = new Long[1];
            String token = createGroupWith(0, holder).get(0);
            insertBackground("GYM", "헬스장", 100);
            insertBackground("STUDY_ROOM", "독서실", 100);

            getShop(token, holder[0], false)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].name").value("헬스장"));
        }

        @Test
        @DisplayName("그룹 멤버가 아니면 403 이다")
        void nonMemberIsForbidden() throws Exception {
            Long[] holder = new Long[1];
            createGroupWith(0, holder);
            String outsider = loginAs("outsider@example.com", "외부인").accessToken();

            getShop(outsider, holder[0]).andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("구매 제안")
    class CreateRequest {

        @Test
        @DisplayName("제안자는 자동으로 찬성 1표가 된다")
        void requesterAutomaticallyAgrees() throws Exception {
            Long[] holder = new Long[1];
            List<String> tokens = createGroupWith(2, holder);
            Long backgroundId = insertBackground("GYM", "헬스장", 100);
            giveGroupPoints(holder[0], 500);

            createRequest(tokens.get(0), holder[0], backgroundId)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.agreeCount").value(1))
                    .andExpect(jsonPath("$.totalMembers").value(3))
                    .andExpect(jsonPath("$.requiredCount").value(2))
                    .andExpect(jsonPath("$.status").value("VOTING"))
                    .andExpect(jsonPath("$.voted").value(true));
        }

        @Test
        @DisplayName("1인 그룹은 제안 즉시 결제되고 적용된다")
        void soloGroupPurchasesImmediately() throws Exception {
            Long[] holder = new Long[1];
            String token = createGroupWith(0, holder).get(0);
            Long backgroundId = insertBackground("GYM", "헬스장", 100);
            giveGroupPoints(holder[0], 500);

            createRequest(token, holder[0], backgroundId)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("APPROVED"));

            assertThat(groupBalanceOf(holder[0])).isEqualTo(400);
            getActiveBackground(token, holder[0])
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.backgroundId").value(backgroundId));
        }

        @Test
        @DisplayName("다른 계열 배경은 제안할 수 없다")
        void mapTypeMismatchIsRejected() throws Exception {
            Long[] holder = new Long[1];
            String token = createGroupWith(1, holder).get(0);
            Long backgroundId = insertBackground("STUDY_ROOM", "독서실", 100);
            giveGroupPoints(holder[0], 500);

            createRequest(token, holder[0], backgroundId).andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("그룹 포인트가 모자라면 제안할 수 없다")
        void insufficientBalanceIsRejected() throws Exception {
            Long[] holder = new Long[1];
            String token = createGroupWith(1, holder).get(0);
            Long backgroundId = insertBackground("GYM", "헬스장", 1000);
            giveGroupPoints(holder[0], 100);

            createRequest(token, holder[0], backgroundId).andExpect(status().isConflict());
        }

        @Test
        @DisplayName("투표 중인 제안이 있으면 새 제안을 할 수 없다")
        void secondRequestIsRejectedWhileVoting() throws Exception {
            Long[] holder = new Long[1];
            List<String> tokens = createGroupWith(2, holder);
            Long first = insertBackground("GYM", "헬스장", 100);
            Long second = insertBackground("GYM", "요가원", 100);
            giveGroupPoints(holder[0], 500);

            createRequest(tokens.get(0), holder[0], first).andExpect(status().isCreated());
            createRequest(tokens.get(1), holder[0], second).andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("투표")
    class Vote {

        @Test
        @DisplayName("찬성이 과반을 넘으면 결제와 적용이 함께 끝난다")
        void majorityApprovalPurchasesAndApplies() throws Exception {
            Long[] holder = new Long[1];
            List<String> tokens = createGroupWith(2, holder);
            Long backgroundId = insertBackground("GYM", "헬스장", 100);
            giveGroupPoints(holder[0], 500);

            createRequest(tokens.get(0), holder[0], backgroundId).andExpect(status().isCreated());
            Long requestId = requestIdOf(holder[0]);

            vote(tokens.get(1), holder[0], requestId, true)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("APPROVED"));

            assertThat(groupBalanceOf(holder[0])).isEqualTo(400);
            assertThat(ownedCountOf(holder[0])).isEqualTo(1);
            getActiveBackground(tokens.get(0), holder[0])
                    .andExpect(jsonPath("$.backgroundId").value(backgroundId));
        }

        @Test
        @DisplayName("가결이 불가능해지면 부결된다")
        void rejectedWhenApprovalBecomesImpossible() throws Exception {
            Long[] holder = new Long[1];
            List<String> tokens = createGroupWith(2, holder);
            Long backgroundId = insertBackground("GYM", "헬스장", 100);
            giveGroupPoints(holder[0], 500);

            createRequest(tokens.get(0), holder[0], backgroundId).andExpect(status().isCreated());
            Long requestId = requestIdOf(holder[0]);

            vote(tokens.get(1), holder[0], requestId, false).andExpect(status().isCreated());
            // 3명 중 과반 2. 반대 2가 되면 찬성이 최대 1이라 가결이 불가능하다.
            vote(tokens.get(2), holder[0], requestId, false)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("REJECTED"));

            assertThat(groupBalanceOf(holder[0])).isEqualTo(500);
        }

        @Test
        @DisplayName("같은 사람이 두 번 투표할 수 없다")
        void doubleVoteIsRejected() throws Exception {
            Long[] holder = new Long[1];
            List<String> tokens = createGroupWith(3, holder);
            Long backgroundId = insertBackground("GYM", "헬스장", 100);
            giveGroupPoints(holder[0], 500);

            createRequest(tokens.get(0), holder[0], backgroundId).andExpect(status().isCreated());
            Long requestId = requestIdOf(holder[0]);

            vote(tokens.get(1), holder[0], requestId, false).andExpect(status().isCreated());
            vote(tokens.get(1), holder[0], requestId, true).andExpect(status().isConflict());
        }

        @Test
        @DisplayName("제안자는 이미 찬성했으므로 다시 투표할 수 없다")
        void requesterCannotVoteAgain() throws Exception {
            Long[] holder = new Long[1];
            List<String> tokens = createGroupWith(3, holder);
            Long backgroundId = insertBackground("GYM", "헬스장", 100);
            giveGroupPoints(holder[0], 500);

            createRequest(tokens.get(0), holder[0], backgroundId).andExpect(status().isCreated());

            vote(tokens.get(0), holder[0], requestIdOf(holder[0]), true).andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("기한 만료")
    class Expiry {

        private void expire(Long requestId) {
            jdbcTemplate.update(
                    "update background_purchase_requests set expires_at = ? where id = ?",
                    java.time.LocalDateTime.now().minusMinutes(1),
                    requestId);
        }

        @Test
        @DisplayName("만료된 제안에는 투표할 수 없다")
        void expiredRequestRejectsVote() throws Exception {
            Long[] holder = new Long[1];
            List<String> tokens = createGroupWith(2, holder);
            Long backgroundId = insertBackground("GYM", "헬스장", 100);
            giveGroupPoints(holder[0], 500);

            createRequest(tokens.get(0), holder[0], backgroundId).andExpect(status().isCreated());
            Long requestId = requestIdOf(holder[0]);
            expire(requestId);

            vote(tokens.get(1), holder[0], requestId, true).andExpect(status().isConflict());

            // 409 를 던지면 트랜잭션이 롤백되어 상태는 그대로다. 정리는 조회와 새 제안이 한다.
            assertThat(requestStatusOf(requestId)).isEqualTo("VOTING");
            getCurrentRequest(tokens.get(0), holder[0]).andExpect(status().isNoContent());
            assertThat(requestStatusOf(requestId)).isEqualTo("REJECTED");
        }

        @Test
        @DisplayName("만료된 제안은 조회 시점에 정리되어 204 가 된다")
        void expiredRequestIsClosedOnRead() throws Exception {
            Long[] holder = new Long[1];
            List<String> tokens = createGroupWith(2, holder);
            Long backgroundId = insertBackground("GYM", "헬스장", 100);
            giveGroupPoints(holder[0], 500);

            createRequest(tokens.get(0), holder[0], backgroundId).andExpect(status().isCreated());
            Long requestId = requestIdOf(holder[0]);
            expire(requestId);

            getCurrentRequest(tokens.get(0), holder[0]).andExpect(status().isNoContent());
            assertThat(requestStatusOf(requestId)).isEqualTo("REJECTED");
        }

        @Test
        @DisplayName("만료된 제안이 있어도 새로 제안할 수 있다")
        void canRequestAgainAfterExpiry() throws Exception {
            Long[] holder = new Long[1];
            List<String> tokens = createGroupWith(2, holder);
            Long first = insertBackground("GYM", "헬스장", 100);
            Long second = insertBackground("GYM", "요가원", 100);
            giveGroupPoints(holder[0], 500);

            createRequest(tokens.get(0), holder[0], first).andExpect(status().isCreated());
            expire(requestIdOf(holder[0]));

            createRequest(tokens.get(1), holder[0], second).andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("제안 취소")
    class CancelRequest {

        @Test
        @DisplayName("제안자가 취소할 수 있다")
        void requesterCanCancel() throws Exception {
            Long[] holder = new Long[1];
            List<String> tokens = createGroupWith(2, holder);
            Long backgroundId = insertBackground("GYM", "헬스장", 100);
            giveGroupPoints(holder[0], 500);

            createRequest(tokens.get(1), holder[0], backgroundId).andExpect(status().isCreated());
            Long requestId = requestIdOf(holder[0]);

            cancelRequest(tokens.get(1), holder[0], requestId).andExpect(status().isNoContent());
            assertThat(requestStatusOf(requestId)).isEqualTo("REJECTED");
        }

        @Test
        @DisplayName("그룹 OWNER 가 남의 제안을 취소할 수 있다")
        void ownerCanCancel() throws Exception {
            Long[] holder = new Long[1];
            List<String> tokens = createGroupWith(2, holder);
            Long backgroundId = insertBackground("GYM", "헬스장", 100);
            giveGroupPoints(holder[0], 500);

            createRequest(tokens.get(1), holder[0], backgroundId).andExpect(status().isCreated());
            Long requestId = requestIdOf(holder[0]);

            cancelRequest(tokens.get(0), holder[0], requestId).andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("제안자도 OWNER 도 아니면 취소할 수 없다")
        void othersCannotCancel() throws Exception {
            Long[] holder = new Long[1];
            List<String> tokens = createGroupWith(2, holder);
            Long backgroundId = insertBackground("GYM", "헬스장", 100);
            giveGroupPoints(holder[0], 500);

            createRequest(tokens.get(1), holder[0], backgroundId).andExpect(status().isCreated());
            Long requestId = requestIdOf(holder[0]);

            cancelRequest(tokens.get(2), holder[0], requestId).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("취소하면 다시 제안할 수 있다")
        void canRequestAgainAfterCancel() throws Exception {
            Long[] holder = new Long[1];
            List<String> tokens = createGroupWith(2, holder);
            Long backgroundId = insertBackground("GYM", "헬스장", 100);
            giveGroupPoints(holder[0], 500);

            createRequest(tokens.get(0), holder[0], backgroundId).andExpect(status().isCreated());
            cancelRequest(tokens.get(0), holder[0], requestIdOf(holder[0])).andExpect(status().isNoContent());

            createRequest(tokens.get(1), holder[0], backgroundId).andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("진행 중인 제안 조회")
    class GetCurrent {

        @Test
        @DisplayName("진행 중인 제안이 없으면 204 다")
        void noContentWhenNothingIsVoting() throws Exception {
            Long[] holder = new Long[1];
            String token = createGroupWith(1, holder).get(0);

            getCurrentRequest(token, holder[0]).andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("찬반 집계와 내 투표 여부를 함께 준다")
        void returnsTallyAndMyVote() throws Exception {
            Long[] holder = new Long[1];
            List<String> tokens = createGroupWith(3, holder);
            Long backgroundId = insertBackground("GYM", "헬스장", 100);
            giveGroupPoints(holder[0], 500);

            createRequest(tokens.get(0), holder[0], backgroundId).andExpect(status().isCreated());
            vote(tokens.get(1), holder[0], requestIdOf(holder[0]), false).andExpect(status().isCreated());

            getCurrentRequest(tokens.get(2), holder[0])
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.agreeCount").value(1))
                    .andExpect(jsonPath("$.disagreeCount").value(1))
                    .andExpect(jsonPath("$.totalMembers").value(4))
                    .andExpect(jsonPath("$.requiredCount").value(3))
                    .andExpect(jsonPath("$.voted").value(false))
                    .andExpect(jsonPath("$.requestedByNickname").value(OWNER_NICKNAME));
        }
    }

    @Nested
    @DisplayName("탈퇴 재판정")
    class Reevaluate {

        @Test
        @DisplayName("미투표자가 나가 과반이 차면 그 자리에서 가결된다")
        void leavingShrinksDenominatorAndApproves() throws Exception {
            Long[] holder = new Long[1];
            List<String> tokens = createGroupWith(3, holder);
            Long backgroundId = insertBackground("GYM", "헬스장", 100);
            giveGroupPoints(holder[0], 500);

            // 4명, 과반 3. 방장 찬성 1 + 멤버0 찬성 1 = 2 로 아직 모자라다.
            createRequest(tokens.get(0), holder[0], backgroundId).andExpect(status().isCreated());
            Long requestId = requestIdOf(holder[0]);
            vote(tokens.get(1), holder[0], requestId, true)
                    .andExpect(jsonPath("$.status").value("VOTING"));

            // 미투표자 둘 중 하나가 나가면 3명 과반 2 가 되어 찬성 2로 가결된다.
            leaveGroup(tokens.get(2), holder[0]).andExpect(status().isNoContent());

            assertThat(requestStatusOf(requestId)).isEqualTo("APPROVED");
            assertThat(groupBalanceOf(holder[0])).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("판정 경계")
    class DecisionEdges {

        @Test
        @DisplayName("찬성한 사람이 나가면 그 표가 집계에서 빠진다")
        void voteOfLeftMemberIsExcluded() throws Exception {
            Long[] holder = new Long[1];
            List<String> tokens = createGroupWith(3, holder);
            Long backgroundId = insertBackground("GYM", "헬스장", 100);
            giveGroupPoints(holder[0], 500);

            // 4명, 과반 3. 방장 찬성 1 + 멤버0 찬성 1 = 2, 멤버1 반대.
            createRequest(tokens.get(0), holder[0], backgroundId).andExpect(status().isCreated());
            Long requestId = requestIdOf(holder[0]);
            vote(tokens.get(1), holder[0], requestId, true).andExpect(status().isCreated());
            vote(tokens.get(2), holder[0], requestId, false)
                    .andExpect(jsonPath("$.agreeCount").value(2))
                    .andExpect(jsonPath("$.status").value("VOTING"));

            // 찬성했던 멤버0 이 나간다. 3명 과반 2 인데 그 표를 빼면 찬성 1 이라 아직 가결이 아니다.
            leaveGroup(tokens.get(1), holder[0]).andExpect(status().isNoContent());

            assertThat(requestStatusOf(requestId)).isEqualTo("VOTING");
            assertThat(groupBalanceOf(holder[0])).isEqualTo(500);
            getCurrentRequest(tokens.get(0), holder[0])
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.agreeCount").value(1))
                    .andExpect(jsonPath("$.totalMembers").value(3))
                    .andExpect(jsonPath("$.requiredCount").value(2));
        }

        @Test
        @DisplayName("다른 그룹의 제안에는 투표할 수 없다")
        void cannotVoteOnAnotherGroupRequest() throws Exception {
            Long[] holder = new Long[1];
            List<String> tokens = createGroupWith(1, holder);
            Long backgroundId = insertBackground("GYM", "헬스장", 100);
            giveGroupPoints(holder[0], 500);

            createRequest(tokens.get(0), holder[0], backgroundId).andExpect(status().isCreated());
            Long otherGroupRequestId = requestIdOf(holder[0]);

            // 멤버0 이 자기가 만든 다른 그룹에서 남의 그룹 제안 id 로 투표를 시도한다.
            Long myGroupId = createGroup(tokens.get(1));
            vote(tokens.get(1), myGroupId, otherGroupRequestId, true).andExpect(status().isNotFound());

            assertThat(requestStatusOf(otherGroupRequestId)).isEqualTo("VOTING");
        }

        @Test
        @DisplayName("투표 중에 포인트가 빠지면 가결돼도 결제 대신 부결된다")
        void insufficientBalanceAtSettlementRejects() throws Exception {
            Long[] holder = new Long[1];
            List<String> tokens = createGroupWith(2, holder);
            Long backgroundId = insertBackground("GYM", "헬스장", 300);
            giveGroupPoints(holder[0], 500);

            createRequest(tokens.get(0), holder[0], backgroundId).andExpect(status().isCreated());
            Long requestId = requestIdOf(holder[0]);

            // 투표가 도는 사이 잔액이 가격 아래로 떨어진다.
            giveGroupPoints(holder[0], 100);

            // 3명 과반 2. 찬성 2 로 가결 조건을 채우지만 결제 단계에서 잔액이 모자란다.
            vote(tokens.get(1), holder[0], requestId, true)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("REJECTED"));

            assertThat(groupBalanceOf(holder[0])).isEqualTo(100);
            assertThat(ownedCountOf(holder[0])).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("배경 적용")
    class ApplyBackground {

        @Test
        @DisplayName("보유하지 않은 배경은 적용할 수 없다")
        void cannotApplyUnownedBackground() throws Exception {
            Long[] holder = new Long[1];
            String token = createGroupWith(0, holder).get(0);
            Long backgroundId = insertBackground("GYM", "헬스장", 100);

            applyBackground(token, holder[0], backgroundId).andExpect(status().isConflict());
        }

        @Test
        @DisplayName("OWNER 가 아니면 적용할 수 없다")
        void onlyOwnerCanApply() throws Exception {
            Long[] holder = new Long[1];
            List<String> tokens = createGroupWith(1, holder);
            Long backgroundId = insertBackground("GYM", "헬스장", 100);
            giveGroupPoints(holder[0], 500);

            createRequest(tokens.get(0), holder[0], backgroundId).andExpect(status().isCreated());
            vote(tokens.get(1), holder[0], requestIdOf(holder[0]), true).andExpect(status().isCreated());

            applyBackground(tokens.get(1), holder[0], backgroundId).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("OWNER 는 예전에 산 배경으로 되돌릴 수 있다")
        void ownerCanSwitchBackToOwnedBackground() throws Exception {
            Long[] holder = new Long[1];
            String token = createGroupWith(0, holder).get(0);
            Long first = insertBackground("GYM", "헬스장", 100);
            Long second = insertBackground("GYM", "요가원", 100);
            giveGroupPoints(holder[0], 500);

            // 1인 그룹이라 제안하는 즉시 사고 적용된다. 두 번 사면 나중 것이 걸린다.
            createRequest(token, holder[0], first).andExpect(status().isCreated());
            createRequest(token, holder[0], second).andExpect(status().isCreated());
            getActiveBackground(token, holder[0])
                    .andExpect(jsonPath("$.backgroundId").value(second));

            applyBackground(token, holder[0], first)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.backgroundId").value(first));

            getActiveBackground(token, holder[0])
                    .andExpect(jsonPath("$.backgroundId").value(first));
            assertThat(groupBalanceOf(holder[0])).isEqualTo(300);
        }

        @Test
        @DisplayName("배경을 사지 않은 그룹은 mapType 만 내려온다")
        void unownedGroupFallsBackToMapType() throws Exception {
            Long[] holder = new Long[1];
            String token = createGroupWith(0, holder).get(0);

            getActiveBackground(token, holder[0])
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.backgroundId").isEmpty())
                    .andExpect(jsonPath("$.imageUrl").isEmpty())
                    .andExpect(jsonPath("$.mapType").value("GYM"));
        }
    }

    @Nested
    @DisplayName("동시성")
    class Concurrency {

        @Test
        @DisplayName("마지막 찬성표가 동시에 들어와도 결제는 한 번만 일어난다")
        void concurrentDecidingVotesPurchaseOnce() throws Exception {
            Long[] holder = new Long[1];
            List<String> tokens = createGroupWith(2, holder);
            Long backgroundId = insertBackground("GYM", "헬스장", 100);
            giveGroupPoints(holder[0], 500);

            // 3명, 과반 2. 방장 찬성 1 상태에서 둘이 동시에 찬성한다.
            createRequest(tokens.get(0), holder[0], backgroundId).andExpect(status().isCreated());
            Long requestId = requestIdOf(holder[0]);

            runConcurrently(List.of(
                    () -> vote(tokens.get(1), holder[0], requestId, true),
                    () -> vote(tokens.get(2), holder[0], requestId, true)));

            assertThat(requestStatusOf(requestId)).isEqualTo("APPROVED");
            assertThat(groupBalanceOf(holder[0])).isEqualTo(400);
            assertThat(ownedCountOf(holder[0])).isEqualTo(1);
        }

        @Test
        @DisplayName("동시에 제안해도 투표 중인 제안은 하나만 생긴다")
        void concurrentRequestsCreateOnlyOne() throws Exception {
            Long[] holder = new Long[1];
            List<String> tokens = createGroupWith(1, holder);
            Long first = insertBackground("GYM", "헬스장", 100);
            Long second = insertBackground("GYM", "요가원", 100);
            giveGroupPoints(holder[0], 500);

            runConcurrently(List.of(
                    () -> createRequest(tokens.get(0), holder[0], first),
                    () -> createRequest(tokens.get(1), holder[0], second)));

            Integer voting = jdbcTemplate.queryForObject(
                    "select count(*) from background_purchase_requests where group_id = ? and status = 'VOTING'",
                    Integer.class,
                    holder[0]);
            assertThat(voting).isEqualTo(1);
        }
    }

    @FunctionalInterface
    private interface Call {
        ResultActions run() throws Exception;
    }

    // 두 호출을 같은 순간에 출발시킨다. 예외는 삼킨다 — 한쪽이 409 로 밀리는 것이 정상이다.
    private void runConcurrently(List<Call> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(calls.size());

        for (Call call : calls) {
            pool.submit(() -> {
                try {
                    start.await();
                    call.run();
                } catch (Exception ignored) {
                    // 경합에서 밀린 쪽의 실패는 기대한 결과다
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        done.await(20, TimeUnit.SECONDS);
        pool.shutdown();
    }
}
