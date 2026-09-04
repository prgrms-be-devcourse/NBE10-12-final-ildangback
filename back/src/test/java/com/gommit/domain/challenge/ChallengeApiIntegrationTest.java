package com.gommit.domain.challenge;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

@DisplayName("챌린지 API")
class ChallengeApiIntegrationTest extends IntegrationTestSupport {

    private static final String EMAIL = "gommit@example.com";
    private static final String NICKNAME = "꼬밋러";

    private ResultActions createGroup(String accessToken, String name) throws Exception {
        return mockMvc.perform(jsonRequest(withToken(post("/api/groups"), accessToken), groupCreateBody(name)));
    }

    private ResultActions getChallengeStatus(String accessToken, Long challengeId) throws Exception {
        return mockMvc.perform(withToken(get("/api/challenges/" + challengeId), accessToken));
    }

    private ResultActions getMemberTodayStatuses(String accessToken, Long challengeId) throws Exception {
        return mockMvc.perform(withToken(get("/api/challenges/" + challengeId + "/members"), accessToken));
    }

    private ResultActions updateChallenge(String accessToken, Long challengeId, String body) throws Exception {
        return mockMvc.perform(jsonRequest(withToken(patch("/api/challenges/" + challengeId), accessToken), body));
    }

    private ResultActions delegateOwner(String accessToken, Long challengeId, Long targetUserId) throws Exception {
        return mockMvc.perform(jsonRequest(
                withToken(patch("/api/challenges/" + challengeId + "/owner"), accessToken),
                "{\"targetUserId\":" + targetUserId + "}"));
    }

    private ResultActions updateExtensionChoice(String accessToken, Long challengeId, String choice) throws Exception {
        return mockMvc.perform(jsonRequest(
                withToken(put("/api/challenges/" + challengeId + "/extension/choice"), accessToken),
                "{\"choice\":\"" + choice + "\"}"));
    }

    private ResultActions joinGroup(String accessToken, Long groupId) throws Exception {
        return mockMvc.perform(withToken(post("/api/groups/" + groupId + "/members"), accessToken));
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
                """
                .formatted(name, LocalDate.now().plusDays(1), LocalDate.now().plusDays(7));
    }

    private String updateBody() {
        return """
                {
                  "startDate": "%s",
                  "endDate": "%s",
                  "frequencyType": "EVERY_N_DAYS",
                  "frequencyValue": 2,
                  "daysOfWeek": null,
                  "dailyCheckInCount": 3,
                  "allowedTypes": ["PHOTO"]
                }
                """
                .formatted(LocalDate.now().plusDays(2), LocalDate.now().plusDays(10));
    }

    private Long userIdOf(String email) {
        return jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, email);
    }

    private Long latestGroupIdOf(Long ownerId) {
        return jdbcTemplate.queryForObject(
                "select max(id) from challenge_groups where owner_id = ?", Long.class, ownerId);
    }

    private Long challengeIdOf(Long groupId) {
        return jdbcTemplate.queryForObject("select id from challenges where group_id = ?", Long.class, groupId);
    }

    private Long createGroupAndReturnChallengeId(String accessToken, Long ownerId) throws Exception {
        createGroup(accessToken, "오운완 모임").andExpect(status().isCreated());
        return challengeIdOf(latestGroupIdOf(ownerId));
    }

    private void activateChallenge(Long challengeId) {
        jdbcTemplate.update(
                "update challenges set status = 'ACTIVE', start_date = ?, end_date = ?, updated_at = now() where id = ?",
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(7),
                challengeId);
    }

    @Nested
    @DisplayName("챌린지 현황 조회")
    class ChallengeStatus {

        @Test
        @DisplayName("미인증이면 401")
        void requiresAuthentication() throws Exception {
            mockMvc.perform(get("/api/challenges/1"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("참여 중인 챌린지의 진행 현황을 반환한다")
        void returnsChallengeStatus() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            Long ownerId = userIdOf(EMAIL);
            Long challengeId = createGroupAndReturnChallengeId(tokens.accessToken(), ownerId);

            getChallengeStatus(tokens.accessToken(), challengeId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.challenge.id").value(challengeId))
                    .andExpect(jsonPath("$.challenge.ownerId").value(ownerId))
                    .andExpect(jsonPath("$.totalDays").value(7))
                    .andExpect(jsonPath("$.participantCount").value(1))
                    .andExpect(jsonPath("$.myCurrentCount").value(0));
        }

        @Test
        @DisplayName("시즌 멤버가 아니면 403")
        void rejectsNonMember() throws Exception {
            var ownerTokens = loginAs(EMAIL, NICKNAME);
            Long challengeId = createGroupAndReturnChallengeId(ownerTokens.accessToken(), userIdOf(EMAIL));
            var otherTokens = loginAs("other@example.com", "다른유저");

            getChallengeStatus(otherTokens.accessToken(), challengeId)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NOT_CHALLENGE_MEMBER"));
        }
    }

    @Nested
    @DisplayName("시즌 멤버 오늘 인증 현황 조회")
    class MemberTodayStatuses {

        @Test
        @DisplayName("참여 중인 멤버들의 닉네임과 오늘 인증 횟수를 반환한다")
        void returnsMemberTodayStatuses() throws Exception {
            var ownerTokens = loginAs(EMAIL, NICKNAME);
            Long ownerId = userIdOf(EMAIL);
            createGroup(ownerTokens.accessToken(), "오운완 모임").andExpect(status().isCreated());
            Long groupId = latestGroupIdOf(ownerId);
            Long challengeId = challengeIdOf(groupId);
            var memberTokens = loginAs("member@example.com", "새멤버");
            joinGroup(memberTokens.accessToken(), groupId).andExpect(status().isCreated());

            getMemberTodayStatuses(ownerTokens.accessToken(), challengeId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].nickname").value(NICKNAME))
                    .andExpect(jsonPath("$[0].todayCheckInCount").value(0))
                    .andExpect(jsonPath("$[1].nickname").value("새멤버"));
        }
    }

    @Nested
    @DisplayName("READY 챌린지 설정 수정")
    class UpdateChallenge {

        @Test
        @DisplayName("OWNER가 READY 챌린지를 수정하면 변경된 설정을 반환한다")
        void updatesReadyChallenge() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            Long challengeId = createGroupAndReturnChallengeId(tokens.accessToken(), userIdOf(EMAIL));

            updateChallenge(tokens.accessToken(), challengeId, updateBody())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(challengeId))
                    .andExpect(jsonPath("$.frequencyType").value("EVERY_N_DAYS"))
                    .andExpect(jsonPath("$.frequencyValue").value(2))
                    .andExpect(jsonPath("$.dailyCheckInCount").value(3));
        }

        @Test
        @DisplayName("OWNER가 아니면 403")
        void rejectsNonOwner() throws Exception {
            var ownerTokens = loginAs(EMAIL, NICKNAME);
            Long ownerId = userIdOf(EMAIL);
            createGroup(ownerTokens.accessToken(), "오운완 모임").andExpect(status().isCreated());
            Long groupId = latestGroupIdOf(ownerId);
            Long challengeId = challengeIdOf(groupId);
            var memberTokens = loginAs("member@example.com", "새멤버");
            joinGroup(memberTokens.accessToken(), groupId).andExpect(status().isCreated());

            updateChallenge(memberTokens.accessToken(), challengeId, updateBody())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NOT_CHALLENGE_OWNER"));
        }

        @Test
        @DisplayName("READY 상태가 아니면 403")
        void rejectsNonReadyChallenge() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            Long challengeId = createGroupAndReturnChallengeId(tokens.accessToken(), userIdOf(EMAIL));
            activateChallenge(challengeId);

            updateChallenge(tokens.accessToken(), challengeId, updateBody())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("CHALLENGE_NOT_EDITABLE"));
        }
    }

    @Nested
    @DisplayName("시즌 OWNER 위임")
    class DelegateOwner {

        @Test
        @DisplayName("다른 참여 멤버에게 OWNER를 위임한다")
        void delegatesOwner() throws Exception {
            var ownerTokens = loginAs(EMAIL, NICKNAME);
            Long ownerId = userIdOf(EMAIL);
            createGroup(ownerTokens.accessToken(), "오운완 모임").andExpect(status().isCreated());
            Long groupId = latestGroupIdOf(ownerId);
            Long challengeId = challengeIdOf(groupId);
            var memberTokens = loginAs("member@example.com", "새멤버");
            Long memberId = userIdOf("member@example.com");
            joinGroup(memberTokens.accessToken(), groupId).andExpect(status().isCreated());

            delegateOwner(ownerTokens.accessToken(), challengeId, memberId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.previousOwnerId").value(ownerId))
                    .andExpect(jsonPath("$.newOwnerId").value(memberId));

            String role = jdbcTemplate.queryForObject(
                    "select role from challenge_members where challenge_id = ? and user_id = ?",
                    String.class,
                    challengeId,
                    memberId);
            org.assertj.core.api.Assertions.assertThat(role).isEqualTo("OWNER");
        }

        @Test
        @DisplayName("자기 자신에게 위임하면 400")
        void rejectsSelfDelegation() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            Long ownerId = userIdOf(EMAIL);
            Long challengeId = createGroupAndReturnChallengeId(tokens.accessToken(), ownerId);

            delegateOwner(tokens.accessToken(), challengeId, ownerId)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CANNOT_DELEGATE_TO_SELF"));
        }
    }

    @Nested
    @DisplayName("다음 시즌 연장 참여 의사 선택")
    class ExtensionChoice {

        @Test
        @DisplayName("ACTIVE 챌린지 멤버가 EXTEND를 선택하면 선택 현황을 반환한다")
        void updatesExtensionChoice() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            Long ownerId = userIdOf(EMAIL);
            Long challengeId = createGroupAndReturnChallengeId(tokens.accessToken(), ownerId);
            activateChallenge(challengeId);

            updateExtensionChoice(tokens.accessToken(), challengeId, "EXTEND")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.challengeId").value(challengeId))
                    .andExpect(jsonPath("$.userId").value(ownerId))
                    .andExpect(jsonPath("$.choice").value("EXTEND"))
                    .andExpect(jsonPath("$.extendCount").value(1));
        }

        @Test
        @DisplayName("READY 챌린지에서는 연장 의사를 선택할 수 없다")
        void rejectsChoiceWhenChallengeIsNotActive() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            Long challengeId = createGroupAndReturnChallengeId(tokens.accessToken(), userIdOf(EMAIL));

            updateExtensionChoice(tokens.accessToken(), challengeId, "EXTEND")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("EXTENSION_CHOICE_NOT_AVAILABLE"));
        }
    }
}
