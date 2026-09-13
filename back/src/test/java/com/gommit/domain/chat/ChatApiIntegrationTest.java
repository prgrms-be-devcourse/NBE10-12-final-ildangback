package com.gommit.domain.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gommit.support.IntegrationTestSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

@DisplayName("그룹 채팅 API")
class ChatApiIntegrationTest extends IntegrationTestSupport {

    private static final String OWNER_EMAIL = "owner@example.com";
    private static final String OWNER_NICKNAME = "방장";
    private static final String MEMBER_EMAIL = "member@example.com";
    private static final String MEMBER_NICKNAME = "멤버";
    private static final String STRANGER_EMAIL = "stranger@example.com";
    private static final String STRANGER_NICKNAME = "지나가는사람";

    private ResultActions getMessages(String accessToken, Long groupId, String queryString) throws Exception {
        return mockMvc.perform(withToken(get("/api/groups/" + groupId + "/messages" + queryString), accessToken));
    }

    private String groupCreateBody(String name) {
        return """
                {
                  "name": "%s",
                  "description": "함께 인증하는 그룹",
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
                """.formatted(name, LocalDate.now().plusDays(1), LocalDate.now().plusDays(7));
    }

    private Long userIdOf(String email) {
        return jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, email);
    }

    private Long createGroupAndReturnId(String accessToken, Long ownerId) throws Exception {
        mockMvc.perform(jsonRequest(withToken(post("/api/groups"), accessToken), groupCreateBody("오운완 모임")))
                .andExpect(status().isCreated());
        return jdbcTemplate.queryForObject(
                "select max(id) from challenge_groups where owner_id = ?", Long.class, ownerId);
    }

    private void joinGroup(String accessToken, Long groupId) throws Exception {
        mockMvc.perform(withToken(post("/api/groups/" + groupId + "/members"), accessToken))
                .andExpect(status().isCreated());
    }

    // 강퇴는 ACTIVE 챌린지에서만 가능하다
    private void kickMember(String ownerAccessToken, Long groupId, Long targetUserId) throws Exception {
        jdbcTemplate.update("update challenges set status = 'ACTIVE' where group_id = ?", groupId);
        mockMvc.perform(withToken(delete("/api/groups/" + groupId + "/members/" + targetUserId), ownerAccessToken))
                .andExpect(status().isNoContent());
    }

    private Long insertMessage(Long groupId, Long senderId, String content) {
        jdbcTemplate.update(
                "insert into group_messages (group_id, sender_id, message_type, content, created_at, updated_at)"
                        + " values (?, ?, 'TEXT', ?, now(6), now(6))",
                groupId,
                senderId,
                content);
        return jdbcTemplate.queryForObject(
                "select max(id) from group_messages where group_id = ?", Long.class, groupId);
    }

    private String readBody(Long lastReadMessageId) {
        return """
                {"lastReadMessageId": %d}
                """.formatted(lastReadMessageId);
    }

    private ResultActions markRead(String accessToken, Long groupId, Long lastReadMessageId) throws Exception {
        return mockMvc.perform(jsonRequest(
                withToken(put("/api/groups/" + groupId + "/messages/read"), accessToken), readBody(lastReadMessageId)));
    }

    private ResultActions getUnreadCount(String accessToken, Long groupId) throws Exception {
        return mockMvc.perform(withToken(get("/api/groups/" + groupId + "/messages/unread-count"), accessToken));
    }

    private Long readCursorOf(Long groupId, Long userId) {
        return jdbcTemplate.queryForObject(
                "select last_read_message_id from group_members where group_id = ? and user_id = ?",
                Long.class,
                groupId,
                userId);
    }

    @Nested
    @DisplayName("메시지 목록 조회")
    class GetMessages {

        @Test
        @DisplayName("미인증이면 401")
        void requiresAuthentication() throws Exception {
            mockMvc.perform(get("/api/groups/1/messages"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("그룹 멤버가 아니면 403")
        void rejectsNonMember() throws Exception {
            var owner = loginAs(OWNER_EMAIL, OWNER_NICKNAME);
            Long groupId = createGroupAndReturnId(owner.accessToken(), userIdOf(OWNER_EMAIL));

            var stranger = loginAs(STRANGER_EMAIL, STRANGER_NICKNAME);

            getMessages(stranger.accessToken(), groupId, "")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NOT_GROUP_MEMBER"));
        }

        @Test
        @DisplayName("퇴장한 멤버는 조회할 수 없다")
        void rejectsLeftMember() throws Exception {
            var owner = loginAs(OWNER_EMAIL, OWNER_NICKNAME);
            Long groupId = createGroupAndReturnId(owner.accessToken(), userIdOf(OWNER_EMAIL));

            var member = loginAs(MEMBER_EMAIL, MEMBER_NICKNAME);
            joinGroup(member.accessToken(), groupId);
            mockMvc.perform(withToken(delete("/api/groups/" + groupId + "/members/me"), member.accessToken()))
                    .andExpect(status().isNoContent());

            getMessages(member.accessToken(), groupId, "")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NOT_GROUP_MEMBER"));
        }

        @Test
        @DisplayName("강퇴당한 멤버는 조회할 수 없다")
        void rejectsKickedMember() throws Exception {
            var owner = loginAs(OWNER_EMAIL, OWNER_NICKNAME);
            Long groupId = createGroupAndReturnId(owner.accessToken(), userIdOf(OWNER_EMAIL));

            var member = loginAs(MEMBER_EMAIL, MEMBER_NICKNAME);
            joinGroup(member.accessToken(), groupId);
            kickMember(owner.accessToken(), groupId, userIdOf(MEMBER_EMAIL));

            getMessages(member.accessToken(), groupId, "")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NOT_GROUP_MEMBER"));
        }

        @Test
        @DisplayName("멤버는 최신순으로 메시지와 보낸 사람 닉네임을 받는다")
        void returnsMessagesInLatestOrder() throws Exception {
            var owner = loginAs(OWNER_EMAIL, OWNER_NICKNAME);
            Long ownerId = userIdOf(OWNER_EMAIL);
            Long groupId = createGroupAndReturnId(owner.accessToken(), ownerId);

            insertMessage(groupId, ownerId, "첫 메시지");
            insertMessage(groupId, ownerId, "두 번째 메시지");

            getMessages(owner.accessToken(), groupId, "")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].content").value("두 번째 메시지"))
                    .andExpect(jsonPath("$.content[0].senderNickname").value(OWNER_NICKNAME))
                    .andExpect(jsonPath("$.content[0].messageType").value("TEXT"))
                    .andExpect(jsonPath("$.hasNext").value(false))
                    .andExpect(jsonPath("$.nextCursor").value(nullValue()));
        }

        @Test
        @DisplayName("커서로 다음 페이지를 이어서 받는다")
        void pagesWithCursor() throws Exception {
            var owner = loginAs(OWNER_EMAIL, OWNER_NICKNAME);
            Long ownerId = userIdOf(OWNER_EMAIL);
            Long groupId = createGroupAndReturnId(owner.accessToken(), ownerId);

            insertMessage(groupId, ownerId, "하나");
            insertMessage(groupId, ownerId, "둘");
            insertMessage(groupId, ownerId, "셋");

            String body = getMessages(owner.accessToken(), groupId, "?size=2")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.hasNext").value(true))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            getMessages(owner.accessToken(), groupId, "?size=2&cursor=" + fieldOf(body, "nextCursor"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].content").value("하나"))
                    .andExpect(jsonPath("$.hasNext").value(false));
        }

        @Test
        @DisplayName("다른 그룹의 메시지는 섞이지 않는다")
        void isolatesGroups() throws Exception {
            var owner = loginAs(OWNER_EMAIL, OWNER_NICKNAME);
            Long ownerId = userIdOf(OWNER_EMAIL);
            Long groupId = createGroupAndReturnId(owner.accessToken(), ownerId);
            Long otherGroupId = createGroupAndReturnId(owner.accessToken(), ownerId);

            insertMessage(groupId, ownerId, "이 그룹 메시지");
            insertMessage(otherGroupId, ownerId, "다른 그룹 메시지");

            getMessages(owner.accessToken(), groupId, "")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].content").value("이 그룹 메시지"));
        }
    }

    @Nested
    @DisplayName("읽음 커서 갱신")
    class MarkRead {

        @Test
        @DisplayName("미인증이면 401")
        void requiresAuthentication() throws Exception {
            mockMvc.perform(jsonRequest(put("/api/groups/1/messages/read"), readBody(1L)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("그룹 멤버가 아니면 403")
        void rejectsNonMember() throws Exception {
            var owner = loginAs(OWNER_EMAIL, OWNER_NICKNAME);
            Long ownerId = userIdOf(OWNER_EMAIL);
            Long groupId = createGroupAndReturnId(owner.accessToken(), ownerId);
            Long messageId = insertMessage(groupId, ownerId, "하나");

            var stranger = loginAs(STRANGER_EMAIL, STRANGER_NICKNAME);

            markRead(stranger.accessToken(), groupId, messageId)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NOT_GROUP_MEMBER"));
        }

        @Test
        @DisplayName("강퇴당한 멤버는 갱신할 수 없다")
        void rejectsKickedMember() throws Exception {
            var owner = loginAs(OWNER_EMAIL, OWNER_NICKNAME);
            Long ownerId = userIdOf(OWNER_EMAIL);
            Long groupId = createGroupAndReturnId(owner.accessToken(), ownerId);
            Long messageId = insertMessage(groupId, ownerId, "하나");

            var member = loginAs(MEMBER_EMAIL, MEMBER_NICKNAME);
            joinGroup(member.accessToken(), groupId);
            kickMember(owner.accessToken(), groupId, userIdOf(MEMBER_EMAIL));

            markRead(member.accessToken(), groupId, messageId)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NOT_GROUP_MEMBER"));
        }

        @Test
        @DisplayName("lastReadMessageId 가 없으면 400")
        void rejectsMissingMessageId() throws Exception {
            var owner = loginAs(OWNER_EMAIL, OWNER_NICKNAME);
            Long groupId = createGroupAndReturnId(owner.accessToken(), userIdOf(OWNER_EMAIL));

            mockMvc.perform(jsonRequest(
                            withToken(put("/api/groups/" + groupId + "/messages/read"), owner.accessToken()), "{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
        }

        @Test
        @DisplayName("멤버가 읽으면 커서가 저장된다")
        void savesCursor() throws Exception {
            var owner = loginAs(OWNER_EMAIL, OWNER_NICKNAME);
            Long ownerId = userIdOf(OWNER_EMAIL);
            Long groupId = createGroupAndReturnId(owner.accessToken(), ownerId);
            insertMessage(groupId, ownerId, "하나");
            Long second = insertMessage(groupId, ownerId, "둘");

            markRead(owner.accessToken(), groupId, second).andExpect(status().isNoContent());

            assertThat(readCursorOf(groupId, ownerId)).isEqualTo(second);
        }

        @Test
        @DisplayName("이미 읽은 지점보다 뒤로 가는 갱신은 무시한다")
        void ignoresBackwardCursor() throws Exception {
            var owner = loginAs(OWNER_EMAIL, OWNER_NICKNAME);
            Long ownerId = userIdOf(OWNER_EMAIL);
            Long groupId = createGroupAndReturnId(owner.accessToken(), ownerId);
            Long first = insertMessage(groupId, ownerId, "하나");
            Long second = insertMessage(groupId, ownerId, "둘");

            markRead(owner.accessToken(), groupId, second).andExpect(status().isNoContent());
            markRead(owner.accessToken(), groupId, first).andExpect(status().isNoContent());

            assertThat(readCursorOf(groupId, ownerId)).isEqualTo(second);
        }
    }

    @Nested
    @DisplayName("안 읽은 메시지 수 조회")
    class GetUnreadCount {

        @Test
        @DisplayName("그룹 멤버가 아니면 403")
        void rejectsNonMember() throws Exception {
            var owner = loginAs(OWNER_EMAIL, OWNER_NICKNAME);
            Long groupId = createGroupAndReturnId(owner.accessToken(), userIdOf(OWNER_EMAIL));

            var stranger = loginAs(STRANGER_EMAIL, STRANGER_NICKNAME);

            getUnreadCount(stranger.accessToken(), groupId)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NOT_GROUP_MEMBER"));
        }

        @Test
        @DisplayName("한 번도 읽지 않았으면 전체를 센다")
        void countsAllWhenNeverRead() throws Exception {
            var owner = loginAs(OWNER_EMAIL, OWNER_NICKNAME);
            Long ownerId = userIdOf(OWNER_EMAIL);
            Long groupId = createGroupAndReturnId(owner.accessToken(), ownerId);

            var member = loginAs(MEMBER_EMAIL, MEMBER_NICKNAME);
            joinGroup(member.accessToken(), groupId);
            insertMessage(groupId, ownerId, "하나");
            insertMessage(groupId, ownerId, "둘");

            getUnreadCount(member.accessToken(), groupId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.unreadCount").value(2));
        }

        @Test
        @DisplayName("읽은 뒤에는 그 뒤에 온 메시지만 센다")
        void countsOnlyAfterCursor() throws Exception {
            var owner = loginAs(OWNER_EMAIL, OWNER_NICKNAME);
            Long ownerId = userIdOf(OWNER_EMAIL);
            Long groupId = createGroupAndReturnId(owner.accessToken(), ownerId);

            var member = loginAs(MEMBER_EMAIL, MEMBER_NICKNAME);
            joinGroup(member.accessToken(), groupId);
            insertMessage(groupId, ownerId, "하나");
            Long second = insertMessage(groupId, ownerId, "둘");
            insertMessage(groupId, ownerId, "셋");

            markRead(member.accessToken(), groupId, second).andExpect(status().isNoContent());

            getUnreadCount(member.accessToken(), groupId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.unreadCount").value(1));
        }

        @Test
        @DisplayName("다른 그룹 메시지는 세지 않는다")
        void isolatesGroups() throws Exception {
            var owner = loginAs(OWNER_EMAIL, OWNER_NICKNAME);
            Long ownerId = userIdOf(OWNER_EMAIL);
            Long groupId = createGroupAndReturnId(owner.accessToken(), ownerId);
            Long otherGroupId = createGroupAndReturnId(owner.accessToken(), ownerId);

            insertMessage(groupId, ownerId, "이 그룹 메시지");
            insertMessage(otherGroupId, ownerId, "다른 그룹 메시지");
            insertMessage(otherGroupId, ownerId, "다른 그룹 메시지 둘");

            getUnreadCount(owner.accessToken(), groupId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.unreadCount").value(1));
        }
    }
}
