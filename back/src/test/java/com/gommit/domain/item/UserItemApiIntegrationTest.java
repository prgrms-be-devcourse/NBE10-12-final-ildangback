package com.gommit.domain.item;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gommit.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// IntegrationTestSupport를 상속받아 MySQL Testcontainer + MockMvc 환경을 재사용한다.
// @BeforeEach에서 clearDatabase()가 실행되므로 각 테스트는 빈 DB 상태에서 시작한다.
@DisplayName("보유 아이템/캐릭터 API")
class UserItemApiIntegrationTest extends IntegrationTestSupport {

    // ─── 공통 헬퍼 ───────────────────────────────────────────────────────────

    // items 테이블에 아이템을 직접 삽입하고 생성된 id를 반환한다.
    private long insertItem(String slot, String name, int price) {
        jdbcTemplate.update(
                "INSERT INTO items (slot, name, image_url, price, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, NOW(), NOW())",
                slot,
                name,
                "https://cdn.phototourl.com/free/2026-09-02-404c3e23-3aa1-46f2-b0e2-4e2c239530ce.jpg",
                price);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    // user_items 테이블에 미착용 상태(equipped_slot=NULL)로 직접 삽입한다.
    // 구매 API를 거치지 않고 보유 상태를 만들어야 할 때 사용한다.
    private long insertUserItem(long userId, long itemId) {
        jdbcTemplate.update(
                "INSERT INTO user_items (user_id, item_id, equipped_slot, created_at, updated_at) "
                        + "VALUES (?, ?, NULL, NOW(), NOW())",
                userId,
                itemId);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    // loginAs()로 가입된 유저의 DB id를 이메일로 조회한다.
    // @CurrentUser가 반환하는 userId와 동일하다.
    private long getUserId(String email) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    // ─── 내 캐릭터 조회 ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("내 캐릭터 조회 GET /api/users/me/character")
    class GetMyCharacter {

        @Test
        @DisplayName("미인증이면 401")
        void 미인증_401() throws Exception {
            mockMvc.perform(get("/api/users/me/character"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("인증하면 200 과 슬롯 맵을 돌려준다")
        void 인증_200() throws Exception {
            var tokens = loginAs();

            mockMvc.perform(withToken(get("/api/users/me/character"), tokens.accessToken()))
                    .andExpect(status().isOk())
                    // CharacterResponse의 slots 필드(Map<ItemSlot, String>)가 존재하는지 확인
                    .andExpect(jsonPath("$.slots").exists());
        }
    }

    // ─── 보유 아이템 조회 ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("보유 아이템 조회 GET /api/users/me/items")
    class GetMyItems {

        @Test
        @DisplayName("미인증이면 401")
        void 미인증_401() throws Exception {
            mockMvc.perform(get("/api/users/me/items")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("인증하면 200 과 SliceResponse 구조를 돌려준다")
        void 인증_200() throws Exception {
            var tokens = loginAs();

            // 보유 아이템이 없어도 빈 content 배열과 hasNext=false로 200이 응답되어야 한다.
            mockMvc.perform(withToken(get("/api/users/me/items"), tokens.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.hasNext").isBoolean());
        }

        @Test
        @DisplayName("size=0 이면 400 — @Min(1) 제약")
        void size_0이면_400() throws Exception {
            var tokens = loginAs();

            // ItemController와 동일하게 @Min(1)이 컨트롤러에 선언되어 있으므로
            // Bean Validation이 서비스 호출 전에 400을 반환한다.
            mockMvc.perform(withToken(get("/api/users/me/items").param("size", "0"), tokens.accessToken()))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─── 아이템 착용 ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("아이템 착용 PUT /api/users/me/items/{userItemId}/equip")
    class EquipItem {

        @Test
        @DisplayName("미인증이면 401")
        void 미인증_401() throws Exception {
            mockMvc.perform(put("/api/users/me/items/1/equip")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("보유한 아이템 착용 시 200 과 equippedSlot 이 채워진다")
        void 착용성공_200() throws Exception {
            String email = "equip@example.com";
            // 이 테스트 전용 이메일과 닉네임을 사용해 다른 테스트와 충돌을 방지한다.
            var tokens = loginAs(email, "착용테스터");
            long userId = getUserId(email); // DB에서 직접 id를 가져온다
            long itemId = insertItem("HEAD", "기본 모자", 100);
            // 구매 API 대신 DB 직접 삽입으로 보유 상태를 만든다 (테스트 속도 향상)
            long userItemId = insertUserItem(userId, itemId);

            mockMvc.perform(withToken(put("/api/users/me/items/" + userItemId + "/equip"), tokens.accessToken()))
                    .andExpect(status().isOk())
                    // 착용 후 equippedSlot이 해당 슬롯 문자열로 채워져야 한다
                    .andExpect(jsonPath("$.equippedSlot").value("HEAD"));
        }

        @Test
        @DisplayName("존재하지 않는 userItemId 착용 시 404")
        void 없는아이템_404() throws Exception {
            var tokens = loginAs();

            // DB에 없는 userItemId → UserItemService에서 USER_ITEM_NOT_FOUND → 404
            mockMvc.perform(withToken(put("/api/users/me/items/999999/equip"), tokens.accessToken()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_ITEM_NOT_FOUND"));
        }

        @Test
        @DisplayName("다른 유저의 아이템 착용 시도 시 403")
        void 타인아이템_403() throws Exception {
            // 소유자 계정을 만들고 아이템을 보유시킨다
            String ownerEmail = "owner@example.com";
            loginAs(ownerEmail, "소유자");
            long ownerId = getUserId(ownerEmail);
            long itemId = insertItem("HEAD", "기본 모자", 100);
            long userItemId = insertUserItem(ownerId, itemId); // 소유자의 userItem

            // 별개의 유저가 소유자의 userItemId로 착용 시도
            // isOwnedBy(userId) 검사에서 false → NOT_ITEM_OWNER → 403
            var other = loginAs("other@example.com", "타인");
            mockMvc.perform(withToken(put("/api/users/me/items/" + userItemId + "/equip"), other.accessToken()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NOT_ITEM_OWNER"));
        }

        @Test
        @DisplayName("이미 착용 중인 아이템 재착용 시 409")
        void 이미착용중_409() throws Exception {
            String email = "alreadyequip@example.com";
            var tokens = loginAs(email, "중복착용테스터");
            long userId = getUserId(email);
            long itemId = insertItem("HEAD", "기본 모자", 100);
            long userItemId = insertUserItem(userId, itemId);

            // 첫 번째 착용 (성공)
            mockMvc.perform(withToken(put("/api/users/me/items/" + userItemId + "/equip"), tokens.accessToken()));

            // 두 번째 착용 시도 → isEquipped()가 true → ALREADY_EQUIPPED → 409
            mockMvc.perform(withToken(put("/api/users/me/items/" + userItemId + "/equip"), tokens.accessToken()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ALREADY_EQUIPPED"));
        }
    }

    // ─── 아이템 착용 해제 ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("아이템 착용 해제 DELETE /api/users/me/items/{userItemId}/equip")
    class UnequipItem {

        @Test
        @DisplayName("미인증이면 401")
        void 미인증_401() throws Exception {
            mockMvc.perform(delete("/api/users/me/items/1/equip")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("착용 중인 아이템 해제 시 200 과 equippedSlot 이 사라진다")
        void 해제성공_200() throws Exception {
            String email = "unequip@example.com";
            var tokens = loginAs(email, "해제테스터");
            long userId = getUserId(email);
            long itemId = insertItem("HEAD", "기본 모자", 100);
            long userItemId = insertUserItem(userId, itemId);

            // 먼저 착용 상태로 만든다
            mockMvc.perform(withToken(put("/api/users/me/items/" + userItemId + "/equip"), tokens.accessToken()));

            // 착용 해제 요청 → unequip() 호출 → equippedSlot = null
            mockMvc.perform(withToken(delete("/api/users/me/items/" + userItemId + "/equip"), tokens.accessToken()))
                    .andExpect(status().isOk())
                    // Jackson이 null 필드를 직렬화하는 방식에 따라
                    // doesNotExist() 또는 value((Object) null) 중 하나로 조정한다
                    .andExpect(jsonPath("$.equippedSlot").doesNotExist());
        }

        @Test
        @DisplayName("미착용 상태 아이템 해제 시 400")
        void 미착용해제_400() throws Exception {
            String email = "notequip@example.com";
            var tokens = loginAs(email, "미착용테스터");
            long userId = getUserId(email);
            long itemId = insertItem("HEAD", "기본 모자", 100);
            // insertUserItem은 equipped_slot=NULL로 삽입하므로 미착용 상태다
            long userItemId = insertUserItem(userId, itemId);

            // isEquipped()가 false → NOT_EQUIPPED(HttpStatus.BAD_REQUEST) → 400
            mockMvc.perform(withToken(delete("/api/users/me/items/" + userItemId + "/equip"), tokens.accessToken()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("NOT_EQUIPPED"));
        }

        @Test
        @DisplayName("다른 유저의 아이템 해제 시도 시 403")
        void 타인아이템_403() throws Exception {
            // 소유자가 아이템을 보유하고 착용한다
            String ownerEmail = "owner2@example.com";
            var ownerTokens = loginAs(ownerEmail, "소유자2");
            long ownerId = getUserId(ownerEmail);
            long itemId = insertItem("TOP", "기본 상의", 200);
            long userItemId = insertUserItem(ownerId, itemId);

            mockMvc.perform(withToken(put("/api/users/me/items/" + userItemId + "/equip"), ownerTokens.accessToken()));

            // 타인이 소유자의 착용 아이템을 해제 시도 → NOT_ITEM_OWNER → 403
            var other = loginAs("other2@example.com", "타인2");
            mockMvc.perform(withToken(delete("/api/users/me/items/" + userItemId + "/equip"), other.accessToken()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NOT_ITEM_OWNER"));
        }
    }
}
