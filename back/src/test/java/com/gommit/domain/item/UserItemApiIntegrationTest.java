package com.gommit.domain.item;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gommit.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("보유 아이템/캐릭터 API")
class UserItemApiIntegrationTest extends IntegrationTestSupport {

    // ─── 공통 헬퍼 ───────────────────────────────────────────────────────────

    private long insertItem(String slot, String name, int price) {
        jdbcTemplate.update(
                "INSERT INTO items (slot, name, price, created_at, updated_at) " + "VALUES (?, ?, ?, NOW(), NOW())",
                slot,
                name,
                price);
        long itemId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update(
                "INSERT INTO item_images (item_id, pose, image_key, created_at, updated_at) "
                        + "VALUES (?, 'DEFAULT', ?, NOW(), NOW())",
                itemId,
                "items/test-image.png");

        return itemId;
    }

    private long insertUserItem(long userId, long itemId) {
        jdbcTemplate.update(
                "INSERT INTO user_items (user_id, item_id, equipped_slot, created_at, updated_at) "
                        + "VALUES (?, ?, NULL, NOW(), NOW())",
                userId,
                itemId);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long getUserId(String email) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    // ─── 내 캐릭터 조회 ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("내 캐릭터 조회 GET /api/users/me/character")
    class GetMyCharacter {

        @Test
        @DisplayName("미인증이면 401")
        void t1() throws Exception {
            mockMvc.perform(get("/api/users/me/character"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("인증하면 200 과 슬롯 맵을 돌려준다")
        void t2() throws Exception {
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
        void t3() throws Exception {
            mockMvc.perform(get("/api/users/me/items")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("인증하면 200 과 SliceResponse 구조를 돌려준다")
        void t4() throws Exception {
            var tokens = loginAs();

            mockMvc.perform(withToken(get("/api/users/me/items"), tokens.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.hasNext").isBoolean());
        }

        @Test
        @DisplayName("size=0 이면 400 — @Min(1) 제약")
        void t5() throws Exception {
            var tokens = loginAs();

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
        void t6() throws Exception {
            mockMvc.perform(put("/api/users/me/items/1/equip")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("보유한 아이템 착용 시 200 과 equippedSlot 이 채워진다")
        void t7() throws Exception {
            String email = "equip@example.com";
            var tokens = loginAs(email, "착용테스터");
            long userId = getUserId(email); // DB에서 직접 id를 가져온다
            long itemId = insertItem("HEAD", "기본 모자", 100);
            long userItemId = insertUserItem(userId, itemId);

            mockMvc.perform(withToken(put("/api/users/me/items/" + userItemId + "/equip"), tokens.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.equippedSlot").value("HEAD"));
        }

        @Test
        @DisplayName("존재하지 않는 userItemId 착용 시 404")
        void t8() throws Exception {
            var tokens = loginAs();

            mockMvc.perform(withToken(put("/api/users/me/items/999999/equip"), tokens.accessToken()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_ITEM_NOT_FOUND"));
        }

        @Test
        @DisplayName("다른 유저의 아이템 착용 시도 시 403")
        void t9() throws Exception {
            String ownerEmail = "owner@example.com";
            loginAs(ownerEmail, "소유자");
            long ownerId = getUserId(ownerEmail);
            long itemId = insertItem("HEAD", "기본 모자", 100);
            long userItemId = insertUserItem(ownerId, itemId); // 소유자의 userItem

            var other = loginAs("other@example.com", "타인");
            mockMvc.perform(withToken(put("/api/users/me/items/" + userItemId + "/equip"), other.accessToken()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NOT_ITEM_OWNER"));
        }

        @Test
        @DisplayName("이미 착용 중인 아이템 재착용 시 409")
        void t10() throws Exception {
            String email = "alreadyequip@example.com";
            var tokens = loginAs(email, "중복착용테스터");
            long userId = getUserId(email);
            long itemId = insertItem("HEAD", "기본 모자", 100);
            long userItemId = insertUserItem(userId, itemId);

            mockMvc.perform(withToken(put("/api/users/me/items/" + userItemId + "/equip"), tokens.accessToken()));

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
        void t11() throws Exception {
            mockMvc.perform(delete("/api/users/me/items/1/equip")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("착용 중인 아이템 해제 시 200 과 equippedSlot 이 사라진다")
        void t12() throws Exception {
            String email = "unequip@example.com";
            var tokens = loginAs(email, "해제테스터");
            long userId = getUserId(email);
            long itemId = insertItem("HEAD", "기본 모자", 100);
            long userItemId = insertUserItem(userId, itemId);

            mockMvc.perform(withToken(put("/api/users/me/items/" + userItemId + "/equip"), tokens.accessToken()));

            mockMvc.perform(withToken(delete("/api/users/me/items/" + userItemId + "/equip"), tokens.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.equippedSlot").doesNotExist());
        }

        @Test
        @DisplayName("미착용 상태 아이템 해제 시 400")
        void t13() throws Exception {
            String email = "notequip@example.com";
            var tokens = loginAs(email, "미착용테스터");
            long userId = getUserId(email);
            long itemId = insertItem("HEAD", "기본 모자", 100);
            long userItemId = insertUserItem(userId, itemId);

            mockMvc.perform(withToken(delete("/api/users/me/items/" + userItemId + "/equip"), tokens.accessToken()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("NOT_EQUIPPED"));
        }

        @Test
        @DisplayName("다른 유저의 아이템 해제 시도 시 403")
        void t14() throws Exception {
            String ownerEmail = "owner2@example.com";
            var ownerTokens = loginAs(ownerEmail, "소유자2");
            long ownerId = getUserId(ownerEmail);
            long itemId = insertItem("TOP", "기본 상의", 200);
            long userItemId = insertUserItem(ownerId, itemId);

            mockMvc.perform(withToken(put("/api/users/me/items/" + userItemId + "/equip"), ownerTokens.accessToken()));

            var other = loginAs("other2@example.com", "타인2");
            mockMvc.perform(withToken(delete("/api/users/me/items/" + userItemId + "/equip"), other.accessToken()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NOT_ITEM_OWNER"));
        }
    }
}
