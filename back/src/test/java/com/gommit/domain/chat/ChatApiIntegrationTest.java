package com.gommit.domain.chat;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

    private void insertMessage(Long groupId, Long senderId, String content) {
        jdbcTemplate.update(
                "insert into group_messages (group_id, sender_id, message_type, content, created_at, updated_at)"
                        + " values (?, ?, 'TEXT', ?, now(6), now(6))",
                groupId,
                senderId,
                content);
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
}
