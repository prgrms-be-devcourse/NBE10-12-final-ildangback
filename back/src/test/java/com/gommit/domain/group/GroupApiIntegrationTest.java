package com.gommit.domain.group;

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

@DisplayName("그룹 API")
class GroupApiIntegrationTest extends IntegrationTestSupport {

    private static final String EMAIL = "gommit@example.com";
    private static final String NICKNAME = "꼬밋러";

    private ResultActions createGroup(String accessToken, String body) throws Exception {
        return mockMvc.perform(jsonRequest(withToken(post("/api/groups"), accessToken), body));
    }

    private ResultActions getPublicGroups(String accessToken, String queryString) throws Exception {
        return mockMvc.perform(withToken(get("/api/groups" + queryString), accessToken));
    }

    private ResultActions getMyGroups(String accessToken) throws Exception {
        return mockMvc.perform(withToken(get("/api/groups/me"), accessToken));
    }

    private ResultActions getGroupDetail(String accessToken, Long groupId) throws Exception {
        return mockMvc.perform(withToken(get("/api/groups/" + groupId), accessToken));
    }

    private ResultActions joinGroup(String accessToken, Long groupId) throws Exception {
        return mockMvc.perform(withToken(post("/api/groups/" + groupId + "/members"), accessToken));
    }

    private ResultActions leaveGroup(String accessToken, Long groupId) throws Exception {
        return mockMvc.perform(withToken(delete("/api/groups/" + groupId + "/members/me"), accessToken));
    }

    private String groupCreateBody(String name, String category, String mapType, String visibility) {
        return """
                {
                  "name": "%s",
                  "description": "함께 인증하는 그룹",
                  "category": "%s",
                  "mapType": "%s",
                  "visibility": "%s",
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
                """.formatted(
                        name,
                        category,
                        mapType,
                        visibility,
                        LocalDate.now().plusDays(1),
                        LocalDate.now().plusDays(7));
    }

    private Long userIdOf(String email) {
        return jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, email);
    }

    private Long latestGroupIdOf(Long ownerId) {
        return jdbcTemplate.queryForObject(
                "select max(id) from challenge_groups where owner_id = ?", Long.class, ownerId);
    }

    private Long readyChallengeIdOf(Long groupId) {
        return jdbcTemplate.queryForObject(
                "select id from challenges where group_id = ? and status = 'READY'", Long.class, groupId);
    }

    private Long createGroupAndReturnId(String accessToken, Long ownerId, String name) throws Exception {
        createGroup(accessToken, groupCreateBody(name, "EXERCISE", "GYM", "PUBLIC"))
                .andExpect(status().isCreated());
        return latestGroupIdOf(ownerId);
    }

    @Nested
    @DisplayName("그룹 생성")
    class CreateGroup {

        @Test
        @DisplayName("미인증이면 401")
        void requiresAuthentication() throws Exception {
            mockMvc.perform(jsonRequest(post("/api/groups"), groupCreateBody("오운완 모임", "EXERCISE", "GYM", "PUBLIC")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("그룹을 생성하면 201 과 그룹·챌린지·생성자 멤버를 반환한다")
        void createsGroupWithInitialChallengeAndOwnerMember() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);

            createGroup(tokens.accessToken(), groupCreateBody("오운완 모임", "EXERCISE", "GYM", "PUBLIC"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.group.name").value("오운완 모임"))
                    .andExpect(jsonPath("$.group.currentMembers").value(1))
                    .andExpect(jsonPath("$.currentChallenge.status").value("READY"))
                    .andExpect(jsonPath("$.members[0].nickname").value(NICKNAME));
        }

        @Test
        @DisplayName("카테고리와 맵 타입 조합이 맞지 않으면 400")
        void rejectsInvalidCategoryMapType() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);

            createGroup(tokens.accessToken(), groupCreateBody("운동방", "EXERCISE", "STUDY_ROOM", "PUBLIC"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_CATEGORY_MAP_TYPE"));
        }
    }

    @Nested
    @DisplayName("공개 그룹 목록 조회")
    class PublicGroups {

        @Test
        @DisplayName("미인증이면 401")
        void requiresAuthentication() throws Exception {
            mockMvc.perform(get("/api/groups"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("공개 모집 그룹을 최신순 커서 응답으로 반환한다")
        void returnsPublicGroupsWithCursor() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            Long ownerId = userIdOf(EMAIL);
            createGroupAndReturnId(tokens.accessToken(), ownerId, "아침 오운완");
            createGroupAndReturnId(tokens.accessToken(), ownerId, "저녁 오운완");

            getPublicGroups(tokens.accessToken(), "?keyword=오운완&category=EXERCISE&size=1")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].name").value("저녁 오운완"))
                    .andExpect(jsonPath("$.meta.hasNext").value(true))
                    .andExpect(jsonPath("$.meta.nextCursor").exists());
        }
    }

    @Nested
    @DisplayName("그룹 상세 조회")
    class GroupDetail {

        @Test
        @DisplayName("그룹 기본 정보와 현재 챌린지와 멤버를 반환한다")
        void returnsGroupDetail() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            Long ownerId = userIdOf(EMAIL);
            Long groupId = createGroupAndReturnId(tokens.accessToken(), ownerId, "오운완 모임");

            getGroupDetail(tokens.accessToken(), groupId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.group.id").value(groupId))
                    .andExpect(jsonPath("$.currentChallenge.id").value(readyChallengeIdOf(groupId)))
                    .andExpect(jsonPath("$.members[0].userId").value(ownerId));
        }

        @Test
        @DisplayName("존재하지 않는 그룹이면 404")
        void returns404WhenGroupNotFound() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);

            getGroupDetail(tokens.accessToken(), 999L)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("공개 그룹 참여")
    class JoinGroup {

        @Test
        @DisplayName("공개 모집 그룹에 참여하면 201 과 그룹 멤버·챌린지 멤버 ID를 반환한다")
        void joinsPublicGroup() throws Exception {
            var ownerTokens = loginAs(EMAIL, NICKNAME);
            Long groupId = createGroupAndReturnId(ownerTokens.accessToken(), userIdOf(EMAIL), "오운완 모임");
            var memberTokens = loginAs("member@example.com", "새멤버");
            Long memberId = userIdOf("member@example.com");

            joinGroup(memberTokens.accessToken(), groupId)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.groupMember.userId").value(memberId))
                    .andExpect(jsonPath("$.challengeId").value(readyChallengeIdOf(groupId)))
                    .andExpect(jsonPath("$.challengeMemberId").exists());
        }

        @Test
        @DisplayName("이미 참여한 그룹이면 409")
        void rejectsAlreadyJoinedMember() throws Exception {
            var ownerTokens = loginAs(EMAIL, NICKNAME);
            Long groupId = createGroupAndReturnId(ownerTokens.accessToken(), userIdOf(EMAIL), "오운완 모임");

            joinGroup(ownerTokens.accessToken(), groupId)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ALREADY_JOINED"));
        }

        @Test
        @DisplayName("초대코드 그룹이면 403")
        void rejectsCodeOnlyGroup() throws Exception {
            var ownerTokens = loginAs(EMAIL, NICKNAME);
            createGroup(ownerTokens.accessToken(), groupCreateBody("초대방", "EXERCISE", "GYM", "CODE_ONLY"))
                    .andExpect(status().isCreated());
            Long groupId = latestGroupIdOf(userIdOf(EMAIL));
            var memberTokens = loginAs("member@example.com", "새멤버");

            joinGroup(memberTokens.accessToken(), groupId)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("INVITE_CODE_REQUIRED"));
        }
    }

    @Nested
    @DisplayName("그룹 퇴장")
    class LeaveGroup {

        @Test
        @DisplayName("일반 멤버가 퇴장하면 204 이고 멤버 상태가 LEFT가 된다")
        void leavesGroup() throws Exception {
            var ownerTokens = loginAs(EMAIL, NICKNAME);
            Long groupId = createGroupAndReturnId(ownerTokens.accessToken(), userIdOf(EMAIL), "오운완 모임");
            var memberTokens = loginAs("member@example.com", "새멤버");
            Long memberId = userIdOf("member@example.com");
            joinGroup(memberTokens.accessToken(), groupId).andExpect(status().isCreated());

            leaveGroup(memberTokens.accessToken(), groupId).andExpect(status().isNoContent());

            String status = jdbcTemplate.queryForObject(
                    "select status from group_members where group_id = ? and user_id = ?",
                    String.class,
                    groupId,
                    memberId);
            org.assertj.core.api.Assertions.assertThat(status).isEqualTo("LEFT");
        }

        @Test
        @DisplayName("그룹 OWNER는 바로 퇴장할 수 없다")
        void rejectsOwnerLeave() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            Long groupId = createGroupAndReturnId(tokens.accessToken(), userIdOf(EMAIL), "오운완 모임");

            leaveGroup(tokens.accessToken(), groupId)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("GROUP_OWNER_CANNOT_LEAVE"));
        }
    }

    @Nested
    @DisplayName("내 그룹 목록 조회")
    class MyGroups {

        @Test
        @DisplayName("내가 참여 중인 그룹을 반환한다")
        void returnsMyGroups() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            createGroupAndReturnId(tokens.accessToken(), userIdOf(EMAIL), "오운완 모임");

            getMyGroups(tokens.accessToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].name").value("오운완 모임"))
                    .andExpect(jsonPath("$.meta.hasNext").value(false));
        }
    }
}
