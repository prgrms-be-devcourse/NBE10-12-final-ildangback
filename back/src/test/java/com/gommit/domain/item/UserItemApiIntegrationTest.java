package com.gommit.domain.item;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gommit.domain.item.entity.ItemSlot;
import com.gommit.support.IntegrationTestSupport;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    private long insertGroup(long ownerId, String mapType) {
        jdbcTemplate.update(
                "INSERT INTO challenge_groups "
                        + "(name, category, map_type, visibility, max_members, owner_id, status, created_at, updated_at) "
                        + "VALUES ('테스트 그룹', 'EXERCISE', ?, 'PUBLIC', 6, ?, 'ACTIVE', NOW(), NOW())",
                mapType,
                ownerId);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertChallenge(long groupId, int dailyCheckInCount) {
        return insertChallenge(groupId, dailyCheckInCount, "ACTIVE");
    }

    private long insertChallenge(long groupId, int dailyCheckInCount, String status) {
        jdbcTemplate.update(
                "INSERT INTO challenges "
                        + "(group_id, seq_no, start_date, end_date, status, frequency_type, daily_check_in_count, "
                        + "required_day_count, group_current_streak, group_best_streak, allow_photo, created_at, updated_at) "
                        + "VALUES (?, 1, '2026-01-01', '2026-12-31', ?, 'DAILY', ?, 30, 0, 0, TRUE, NOW(), NOW())",
                groupId,
                status,
                dailyCheckInCount);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void insertChallengeMember(long challengeId, long userId, String role, String status) {
        jdbcTemplate.update(
                "INSERT INTO challenge_members "
                        + "(challenge_id, user_id, role, status, current_streak, best_streak, extension_choice, "
                        + "created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 0, 0, 'PENDING', NOW(), NOW())",
                challengeId,
                userId,
                role,
                status);
    }

    private void insertCheckIn(long challengeId, long userId, int roundNo) {
        insertCheckIn(challengeId, userId, roundNo, businessDate());
    }

    private void insertCheckIn(long challengeId, long userId, int roundNo, LocalDate businessDate) {
        jdbcTemplate.update(
                "INSERT INTO check_ins "
                        + "(challenge_id, user_id, round_no, check_in_type, media_key, media_type, business_date, "
                        + "created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'PHOTO', 'check-ins/test.jpg', 'IMAGE', ?, NOW(), NOW())",
                challengeId,
                userId,
                roundNo,
                businessDate);
    }

    private long insertBareItem(String slot, String name, int price) {
        jdbcTemplate.update(
                "INSERT INTO items (slot, name, price, created_at, updated_at) VALUES (?, ?, ?, NOW(), NOW())",
                slot,
                name,
                price);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void insertItemImage(long itemId, String pose, String imageKey) {
        jdbcTemplate.update(
                "INSERT INTO item_images (item_id, pose, image_key, created_at, updated_at) "
                        + "VALUES (?, ?, ?, NOW(), NOW())",
                itemId,
                pose,
                imageKey);
    }

    private void insertEquippedUserItem(long userId, long itemId, String slot) {
        jdbcTemplate.update(
                "INSERT INTO user_items (user_id, item_id, equipped_slot, created_at, updated_at) "
                        + "VALUES (?, ?, ?, NOW(), NOW())",
                userId,
                itemId,
                slot);
    }

    private LocalDate businessDate() {
        return LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusHours(4).toLocalDate();
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
                    .andExpect(jsonPath("$.slots").exists())
                    .andExpect(jsonPath("$.slots.length()").value(ItemSlot.values().length));
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

    // ─── 챌린지 멤버 캐릭터 조회 ───────────────────────────────────────────────

    @Nested
    @DisplayName("챌린지 멤버 캐릭터 조회 GET /api/challenges/{challengeId}/characters")
    class GetChallengeCharacters {

        @Test
        @DisplayName("미인증이면 401")
        void unauthenticatedIsRejected() throws Exception {
            mockMvc.perform(get("/api/challenges/1/characters"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("당일 인증을 다 채운 멤버만 SUCCESS 자세로 내려온다")
        void completedMemberGetsSuccessPose() throws Exception {
            var tokens = loginAs();
            long myId = getUserId("tester@example.com");
            loginAs("mate@example.com", "메이트");
            long mateId = getUserId("mate@example.com");

            long groupId = insertGroup(myId, "GYM");
            long challengeId = insertChallenge(groupId, 3);
            insertChallengeMember(challengeId, myId, "OWNER", "ACTIVE");
            insertChallengeMember(challengeId, mateId, "MEMBER", "ACTIVE");
            insertCheckIn(challengeId, myId, 1);
            insertCheckIn(challengeId, mateId, 1);
            insertCheckIn(challengeId, mateId, 2);
            insertCheckIn(challengeId, mateId, 3);

            mockMvc.perform(withToken(get("/api/challenges/" + challengeId + "/characters"), tokens.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].userId").value(myId))
                    .andExpect(jsonPath("$[0].nickname").value("테스터"))
                    .andExpect(jsonPath("$[0].pose").value("GYM_FAIL"))
                    .andExpect(jsonPath("$[0].slots").exists())
                    .andExpect(jsonPath("$[1].userId").value(mateId))
                    .andExpect(jsonPath("$[1].pose").value("GYM_SUCCESS"));
        }

        @Test
        @DisplayName("STUDY_ROOM 맵이면 공부 자세로 내려온다")
        void studyRoomMapGetsStudyPose() throws Exception {
            var tokens = loginAs();
            long myId = getUserId("tester@example.com");

            long groupId = insertGroup(myId, "STUDY_ROOM");
            long challengeId = insertChallenge(groupId, 1);
            insertChallengeMember(challengeId, myId, "OWNER", "ACTIVE");

            mockMvc.perform(withToken(get("/api/challenges/" + challengeId + "/characters"), tokens.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].pose").value("STUDY_FAIL"));
        }

        @Test
        @DisplayName("이탈한 멤버가 조회하면 403")
        void leftMemberIsRejected() throws Exception {
            var tokens = loginAs();
            long myId = getUserId("tester@example.com");

            long groupId = insertGroup(myId, "GYM");
            long challengeId = insertChallenge(groupId, 1);
            insertChallengeMember(challengeId, myId, "MEMBER", "LEFT");

            mockMvc.perform(withToken(get("/api/challenges/" + challengeId + "/characters"), tokens.accessToken()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("CHALLENGE_NOT_MEMBER"));
        }

        @Test
        @DisplayName("없는 챌린지면 404")
        void unknownChallengeIsNotFound() throws Exception {
            var tokens = loginAs();

            mockMvc.perform(withToken(get("/api/challenges/999999/characters"), tokens.accessToken()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CHALLENGE_NOT_FOUND"));
        }

        @Test
        @DisplayName("어제 날짜 인증은 오늘 자세에 반영되지 않는다")
        void yesterdayCheckInDoesNotCountForToday() throws Exception {
            var tokens = loginAs();
            long myId = getUserId("tester@example.com");

            long groupId = insertGroup(myId, "GYM");
            long challengeId = insertChallenge(groupId, 1);
            insertChallengeMember(challengeId, myId, "OWNER", "ACTIVE");
            insertCheckIn(challengeId, myId, 1, businessDate().minusDays(1));

            mockMvc.perform(withToken(get("/api/challenges/" + challengeId + "/characters"), tokens.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].pose").value("GYM_FAIL"));
        }

        @Test
        @DisplayName("다른 챌린지의 인증은 이 챌린지 자세에 반영되지 않는다")
        void checkInOfAnotherChallengeDoesNotCount() throws Exception {
            var tokens = loginAs();
            long myId = getUserId("tester@example.com");

            long groupId = insertGroup(myId, "GYM");
            long challengeId = insertChallenge(groupId, 1);
            long otherChallengeId = insertChallenge(insertGroup(myId, "GYM"), 1);
            insertChallengeMember(challengeId, myId, "OWNER", "ACTIVE");
            insertCheckIn(otherChallengeId, myId, 1);

            mockMvc.perform(withToken(get("/api/challenges/" + challengeId + "/characters"), tokens.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].pose").value("GYM_FAIL"));
        }
    }

    // ─── 자세별 이미지 선택 ───────────────────────────────────────────────────

    @Nested
    @DisplayName("자세별 이미지 선택")
    class PoseImageSelection {

        @Test
        @DisplayName("내 캐릭터는 착용 아이템의 DEFAULT 이미지를 쓰고 미착용 슬롯은 null 이다")
        void myCharacterUsesDefaultPoseImage() throws Exception {
            var tokens = loginAs();
            long myId = getUserId("tester@example.com");

            long itemId = insertBareItem("HEAD", "초록 모자", 100);
            insertItemImage(itemId, "DEFAULT", "character-store/hat-default.png");
            insertItemImage(itemId, "GYM_SUCCESS", "character-store/hat-gym-success.png");
            insertEquippedUserItem(myId, itemId, "HEAD");

            mockMvc.perform(withToken(get("/api/users/me/character"), tokens.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.slots.HEAD").value(endsWith("character-store/hat-default.png")))
                    .andExpect(jsonPath("$.slots.length()").value(ItemSlot.values().length))
                    .andExpect(jsonPath("$.slots.TOP").value(nullValue()))
                    .andExpect(jsonPath("$.slots.BOTTOM").value(nullValue()))
                    .andExpect(jsonPath("$.slots.SHOES").value(nullValue()));
        }

        @Test
        @DisplayName("챌린지 화면은 완료 여부에 따라 SUCCESS 와 FAIL 이미지를 각각 쓴다")
        void challengeUsesDifferentImagePerCompletion() throws Exception {
            var tokens = loginAs();
            long myId = getUserId("tester@example.com");
            loginAs("mate@example.com", "메이트");
            long mateId = getUserId("mate@example.com");

            long itemId = insertBareItem("HEAD", "초록 모자", 100);
            insertItemImage(itemId, "DEFAULT", "character-store/hat-default.png");
            insertItemImage(itemId, "GYM_FAIL", "character-store/hat-gym-fail.png");
            insertItemImage(itemId, "GYM_SUCCESS", "character-store/hat-gym-success.png");
            insertEquippedUserItem(myId, itemId, "HEAD");
            insertEquippedUserItem(mateId, itemId, "HEAD");

            long groupId = insertGroup(myId, "GYM");
            long challengeId = insertChallenge(groupId, 2);
            insertChallengeMember(challengeId, myId, "OWNER", "ACTIVE");
            insertChallengeMember(challengeId, mateId, "MEMBER", "ACTIVE");
            insertCheckIn(challengeId, mateId, 1);
            insertCheckIn(challengeId, mateId, 2);

            mockMvc.perform(withToken(get("/api/challenges/" + challengeId + "/characters"), tokens.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].pose").value("GYM_FAIL"))
                    .andExpect(jsonPath("$[0].slots.HEAD").value(endsWith("character-store/hat-gym-fail.png")))
                    .andExpect(jsonPath("$[1].pose").value("GYM_SUCCESS"))
                    .andExpect(jsonPath("$[1].slots.HEAD").value(endsWith("character-store/hat-gym-success.png")));
        }

        @Test
        @DisplayName("그 자세 이미지가 없으면 DEFAULT 이미지로 떨어진다")
        void missingPoseImageFallsBackToDefault() throws Exception {
            var tokens = loginAs();
            long myId = getUserId("tester@example.com");

            long itemId = insertBareItem("HEAD", "기본만 있는 모자", 100);
            insertItemImage(itemId, "DEFAULT", "character-store/hat-default.png");
            insertEquippedUserItem(myId, itemId, "HEAD");

            long groupId = insertGroup(myId, "GYM");
            long challengeId = insertChallenge(groupId, 1);
            insertChallengeMember(challengeId, myId, "OWNER", "ACTIVE");

            mockMvc.perform(withToken(get("/api/challenges/" + challengeId + "/characters"), tokens.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].pose").value("GYM_FAIL"))
                    .andExpect(jsonPath("$[0].slots.HEAD").value(endsWith("character-store/hat-default.png")));
        }

        @Test
        @DisplayName("이미지가 하나도 없는 아이템을 착용하면 그 슬롯은 null 이다")
        void itemWithoutImagesLeavesSlotNull() throws Exception {
            var tokens = loginAs();
            long myId = getUserId("tester@example.com");

            long itemId = insertBareItem("HEAD", "이미지 없는 모자", 100);
            insertEquippedUserItem(myId, itemId, "HEAD");

            mockMvc.perform(withToken(get("/api/users/me/character"), tokens.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.slots.HEAD").value(nullValue()))
                    .andExpect(jsonPath("$.slots.length()").value(ItemSlot.values().length));
        }

        @Test
        @DisplayName("DEFAULT 이미지 행이 뒤에 등록돼 있어도 폴백이 된다")
        void fallsBackToDefaultEvenWhenDefaultRowIsNotFirst() throws Exception {
            var tokens = loginAs();
            long myId = getUserId("tester@example.com");

            long itemId = insertBareItem("HEAD", "성공 자세 없는 모자", 100);
            insertItemImage(itemId, "GYM_FAIL", "character-store/hat-gym-fail.png");
            insertItemImage(itemId, "DEFAULT", "character-store/hat-default.png");
            insertEquippedUserItem(myId, itemId, "HEAD");

            long groupId = insertGroup(myId, "GYM");
            long challengeId = insertChallenge(groupId, 1);
            insertChallengeMember(challengeId, myId, "OWNER", "ACTIVE");
            insertCheckIn(challengeId, myId, 1);

            mockMvc.perform(withToken(get("/api/challenges/" + challengeId + "/characters"), tokens.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].pose").value("GYM_SUCCESS"))
                    .andExpect(jsonPath("$[0].slots.HEAD").value(endsWith("character-store/hat-default.png")));
        }

        @Test
        @DisplayName("그 자세도 DEFAULT 도 없으면 해당 슬롯이 null 이다")
        void slotIsNullWhenNeitherPoseNorDefaultImageExists() throws Exception {
            var tokens = loginAs();
            long myId = getUserId("tester@example.com");

            long itemId = insertBareItem("HEAD", "운동 자세만 있는 모자", 100);
            insertItemImage(itemId, "GYM_FAIL", "character-store/hat-gym-fail.png");
            insertEquippedUserItem(myId, itemId, "HEAD");

            mockMvc.perform(withToken(get("/api/users/me/character"), tokens.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.slots.length()").value(ItemSlot.values().length))
                    .andExpect(jsonPath("$.slots.HEAD").value(nullValue()));
        }

        @Test
        @DisplayName("종료된 챌린지는 인증이 없어도 SUCCESS 자세와 그 자세 이미지를 준다")
        void endedChallengeGivesSuccessPoseAndImage() throws Exception {
            var tokens = loginAs();
            long myId = getUserId("tester@example.com");

            long itemId = insertBareItem("HEAD", "초록 모자", 100);
            insertItemImage(itemId, "DEFAULT", "character-store/hat-default.png");
            insertItemImage(itemId, "GYM_FAIL", "character-store/hat-gym-fail.png");
            insertItemImage(itemId, "GYM_SUCCESS", "character-store/hat-gym-success.png");
            insertEquippedUserItem(myId, itemId, "HEAD");

            long groupId = insertGroup(myId, "GYM");
            long challengeId = insertChallenge(groupId, 2, "ENDED");
            insertChallengeMember(challengeId, myId, "OWNER", "ACTIVE");

            mockMvc.perform(withToken(get("/api/challenges/" + challengeId + "/characters"), tokens.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].pose").value("GYM_SUCCESS"))
                    .andExpect(jsonPath("$[0].slots.HEAD").value(endsWith("character-store/hat-gym-success.png")));
        }
    }

    // ─── 여러 유저 캐릭터 조회 ─────────────────────────────────────────────────

    @Nested
    @DisplayName("여러 유저 캐릭터 조회 GET /api/users/characters")
    class GetCharacters {

        @Test
        @DisplayName("미인증이면 401")
        void unauthenticatedIsRejected() throws Exception {
            mockMvc.perform(get("/api/users/characters").param("userIds", "1")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("userId 별로 DEFAULT 자세 슬롯 맵을 돌려준다")
        void returnsDefaultPoseSlotsPerUser() throws Exception {
            var tokens = loginAs();
            long myId = getUserId("tester@example.com");
            loginAs("mate@example.com", "메이트");
            long mateId = getUserId("mate@example.com");

            long itemId = insertBareItem("HEAD", "초록 모자", 100);
            insertItemImage(itemId, "DEFAULT", "character-store/hat-default.png");
            insertItemImage(itemId, "GYM_SUCCESS", "character-store/hat-gym-success.png");
            insertEquippedUserItem(myId, itemId, "HEAD");

            mockMvc.perform(withToken(
                            get("/api/users/characters").param("userIds", myId + "," + mateId), tokens.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$." + myId + ".HEAD").value(endsWith("character-store/hat-default.png")))
                    .andExpect(jsonPath("$." + mateId + ".HEAD").value(nullValue()))
                    .andExpect(jsonPath("$." + mateId + ".length()").value(ItemSlot.values().length));
        }

        @Test
        @DisplayName("userIds 가 비면 400")
        void emptyUserIdsIsRejected() throws Exception {
            var tokens = loginAs();

            mockMvc.perform(withToken(get("/api/users/characters").param("userIds", ""), tokens.accessToken()))
                    .andExpect(status().isBadRequest());
        }
    }
}
