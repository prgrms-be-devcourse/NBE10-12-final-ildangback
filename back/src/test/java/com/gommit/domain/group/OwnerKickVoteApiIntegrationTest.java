package com.gommit.domain.group;

import static org.assertj.core.api.Assertions.assertThat;
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

@DisplayName("방장 강퇴 투표 API")
class OwnerKickVoteApiIntegrationTest extends IntegrationTestSupport {

    private static final String OWNER_EMAIL = "owner@example.com";
    private static final String OWNER_NICK = "방장";
    private static final String MEMBER1_EMAIL = "member1@example.com";
    private static final String MEMBER1_NICK = "멤버1";
    private static final String MEMBER2_EMAIL = "member2@example.com";
    private static final String MEMBER2_NICK = "멤버2";

    // ── API 헬퍼 ─────────────────────────────────────────────────────────────

    private ResultActions initiateVote(String token, Long groupId) throws Exception {
        return mockMvc.perform(withToken(post("/api/groups/" + groupId + "/ownerKickVotes"), token));
    }

    private ResultActions castVote(String token, Long groupId, String choice) throws Exception {
        return mockMvc.perform(jsonRequest(
                withToken(post("/api/groups/" + groupId + "/ownerKickVotes/cast"), token),
                "{\"choice\":\"" + choice + "\"}"));
    }

    private ResultActions getVoteStatus(String token, Long groupId) throws Exception {
        return mockMvc.perform(withToken(get("/api/groups/" + groupId + "/ownerKickVotes"), token));
    }

    // ── DB 헬퍼 ──────────────────────────────────────────────────────────────

    private Long userIdOf(String email) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private Long latestGroupIdOf(Long ownerId) {
        return jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM challenge_groups WHERE owner_id = ?", Long.class, ownerId);
    }

    private Long firstChallengeIdOf(Long groupId) {
        return jdbcTemplate.queryForObject("SELECT MIN(id) FROM challenges WHERE group_id = ?", Long.class, groupId);
    }

    private String groupMemberStatusOf(Long groupId, Long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM group_members WHERE group_id = ? AND user_id = ?", String.class, groupId, userId);
    }

    private Long ownerIdOf(Long groupId) {
        return jdbcTemplate.queryForObject("SELECT owner_id FROM challenge_groups WHERE id = ?", Long.class, groupId);
    }

    private String kickVoteStartedAtOf(Long groupId) {
        return jdbcTemplate.queryForObject(
                "SELECT kick_vote_started_at FROM challenge_groups WHERE id = ?", String.class, groupId);
    }

    // ── 그룹 설정 헬퍼 ────────────────────────────────────────────────────────

    /** 그룹을 READY 상태로 생성하고 groupId를 반환한다. 멤버는 이 메서드 이후에 가입시켜야 한다. */
    private Long createReadyGroup(Tokens ownerTokens) throws Exception {
        String body = """
                {
                  "name": "테스트 그룹",
                  "description": "설명",
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
        mockMvc.perform(jsonRequest(withToken(post("/api/groups"), ownerTokens.accessToken()), body))
                .andExpect(status().isCreated());
        return latestGroupIdOf(userIdOf(OWNER_EMAIL));
    }

    /** 챌린지와 그룹을 ACTIVE 상태로 전환한다. 반드시 멤버 가입 후에 호출해야 한다. */
    private void activate(Long groupId) {
        jdbcTemplate.update("UPDATE challenges SET status = 'ACTIVE' WHERE id = ?", firstChallengeIdOf(groupId));
        jdbcTemplate.update("UPDATE challenge_groups SET status = 'ACTIVE' WHERE id = ?", groupId);
    }

    private void joinGroup(Tokens memberTokens, Long groupId) throws Exception {
        mockMvc.perform(withToken(post("/api/groups/" + groupId + "/members"), memberTokens.accessToken()))
                .andExpect(status().isCreated());
    }

    /**
     * 투표가 즉시 종료되지 않는 최소 구성: owner + 4명 = 5명 총, n=4.
     * agree(2)*2=4 > 4 → FALSE, disagree(1)*2=2 >= 4 → FALSE → 투표 계속.
     */
    private record FivePersonGroup(Long groupId, Tokens member1, Tokens member2, Tokens member3, Tokens member4) {}

    private FivePersonGroup setupFivePersonActiveGroup() throws Exception {
        var owner = loginAs(OWNER_EMAIL, OWNER_NICK);
        Long groupId = createReadyGroup(owner);
        var m1 = loginAs(MEMBER1_EMAIL, MEMBER1_NICK);
        var m2 = loginAs(MEMBER2_EMAIL, MEMBER2_NICK);
        var m3 = loginAs("member3@example.com", "멤버3");
        var m4 = loginAs("member4@example.com", "멤버4");
        joinGroup(m1, groupId);
        joinGroup(m2, groupId);
        joinGroup(m3, groupId);
        joinGroup(m4, groupId);
        activate(groupId);
        return new FivePersonGroup(groupId, m1, m2, m3, m4);
    }

    /**
     * 투표가 즉시 종료되는 최소 구성: owner + 2명 = 3명 총, n=2.
     * agree(2)*2=4 > 2 → TRUE (가결), disagree(1)*2=2 >= 2 → TRUE (부결).
     */
    private record ThreePersonGroup(Long groupId, Tokens member1, Tokens member2) {}

    private ThreePersonGroup setupThreePersonActiveGroup() throws Exception {
        var owner = loginAs(OWNER_EMAIL, OWNER_NICK);
        Long groupId = createReadyGroup(owner);
        var m1 = loginAs(MEMBER1_EMAIL, MEMBER1_NICK);
        var m2 = loginAs(MEMBER2_EMAIL, MEMBER2_NICK);
        joinGroup(m1, groupId);
        joinGroup(m2, groupId);
        activate(groupId);
        return new ThreePersonGroup(groupId, m1, m2);
    }

    // ── 투표 개시 ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("투표 개시 POST /{groupId}/ownerKickVotes")
    class InitiateVote {

        @Test
        @DisplayName("미인증이면 401")
        void requiresAuthentication() throws Exception {
            mockMvc.perform(post("/api/groups/1/ownerKickVotes")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("그룹 멤버가 아니면 403")
        void rejectsNonMember() throws Exception {
            var owner = loginAs(OWNER_EMAIL, OWNER_NICK);
            Long groupId = createReadyGroup(owner);
            activate(groupId);
            var outsider = loginAs("outsider@example.com", "외부인");

            initiateVote(outsider.accessToken(), groupId)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NOT_GROUP_MEMBER"));
        }

        @Test
        @DisplayName("방장이 투표를 개시하면 403")
        void ownerCannotInitiate() throws Exception {
            var owner = loginAs(OWNER_EMAIL, OWNER_NICK);
            Long groupId = createReadyGroup(owner);
            activate(groupId);

            initiateVote(owner.accessToken(), groupId)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("KICK_VOTE_OWNER_CANNOT_INITIATE"));
        }

        @Test
        @DisplayName("ACTIVE 챌린지가 없으면 409")
        void requiresActiveChallenge() throws Exception {
            var owner = loginAs(OWNER_EMAIL, OWNER_NICK);
            Long groupId = createReadyGroup(owner); // activate 미호출 → READY 상태 유지
            var member1 = loginAs(MEMBER1_EMAIL, MEMBER1_NICK);
            joinGroup(member1, groupId);

            initiateVote(member1.accessToken(), groupId)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("GROUP_MEMBER_KICK_NOT_ALLOWED"));
        }

        @Test
        @DisplayName("일반 멤버가 개시하면 201, 개시자는 자동으로 AGREE 처리된다")
        void memberInitiatesAndIsAutoAgreed() throws Exception {
            // 5명(n=4): agree(1)*2=2 > 4 → FALSE → 투표 지속
            var g = setupFivePersonActiveGroup();

            initiateVote(g.member1().accessToken(), g.groupId())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.inProgress").value(true))
                    .andExpect(jsonPath("$.agreeCount").value(1))
                    .andExpect(jsonPath("$.disagreeCount").value(0))
                    .andExpect(jsonPath("$.eligibleVoters").value(4))
                    .andExpect(jsonPath("$.myChoice").value("AGREE"))
                    .andExpect(jsonPath("$.expiresAt").exists());
        }

        @Test
        @DisplayName("이미 투표가 진행 중이면 409")
        void rejectsDuplicateInitiation() throws Exception {
            var g = setupFivePersonActiveGroup();
            initiateVote(g.member1().accessToken(), g.groupId()).andExpect(status().isCreated());

            initiateVote(g.member2().accessToken(), g.groupId())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("KICK_VOTE_ALREADY_IN_PROGRESS"));
        }

        @Test
        @DisplayName("비OWNER 멤버가 1명일 때 개시하면 즉시 과반수로 방장이 강퇴된다")
        void immediatelyKicksOwnerInTwoMemberGroup() throws Exception {
            // owner + member1 = 2명, n=1, agree(1)*2=2 > 1 → TRUE, 즉시 가결
            var owner = loginAs(OWNER_EMAIL, OWNER_NICK);
            Long groupId = createReadyGroup(owner);
            Long ownerId = userIdOf(OWNER_EMAIL);
            var member1 = loginAs(MEMBER1_EMAIL, MEMBER1_NICK);
            joinGroup(member1, groupId);
            activate(groupId);

            initiateVote(member1.accessToken(), groupId)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.inProgress").value(false));

            assertThat(groupMemberStatusOf(groupId, ownerId)).isEqualTo("KICKED");
            assertThat(ownerIdOf(groupId)).isNotEqualTo(ownerId);
            assertThat(kickVoteStartedAtOf(groupId)).isNull();
        }
    }

    // ── 투표 참여 ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("투표 참여 POST /{groupId}/ownerKickVotes/cast")
    class CastVote {

        @Test
        @DisplayName("진행 중인 투표가 없으면 404")
        void rejectsWhenNoActiveVote() throws Exception {
            var g = setupFivePersonActiveGroup();

            castVote(g.member1().accessToken(), g.groupId(), "AGREE").andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("방장은 자신의 강퇴 투표에 참여할 수 없다 → 403")
        void ownerCannotVote() throws Exception {
            // 3명: member1 개시 후 agree=1, agree*2=2 > 2=n → FALSE, 투표 유지됨
            var owner = loginAs(OWNER_EMAIL, OWNER_NICK);
            Long groupId = createReadyGroup(owner);
            var m1 = loginAs(MEMBER1_EMAIL, MEMBER1_NICK);
            var m2 = loginAs(MEMBER2_EMAIL, MEMBER2_NICK);
            joinGroup(m1, groupId);
            joinGroup(m2, groupId);
            activate(groupId);
            initiateVote(m1.accessToken(), groupId).andExpect(status().isCreated());

            castVote(owner.accessToken(), groupId, "AGREE")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("KICK_VOTE_OWNER_CANNOT_VOTE"));
        }

        @Test
        @DisplayName("AGREE 투표 → 200, agreeCount 증가, 투표 지속")
        void agreeSetsAgreeCount() throws Exception {
            // 5명(n=4): agree(2)*2=4 > 4 → FALSE → 투표 지속
            var g = setupFivePersonActiveGroup();
            initiateVote(g.member1().accessToken(), g.groupId()).andExpect(status().isCreated());

            castVote(g.member2().accessToken(), g.groupId(), "AGREE")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.agreeCount").value(2))
                    .andExpect(jsonPath("$.inProgress").value(true))
                    .andExpect(jsonPath("$.myChoice").value("AGREE"));
        }

        @Test
        @DisplayName("DISAGREE 투표 → 200, disagreeCount 증가, 투표 지속")
        void disagreeSetsDisagreeCount() throws Exception {
            // 5명(n=4): disagree(1)*2=2 >= 4 → FALSE → 투표 지속
            var g = setupFivePersonActiveGroup();
            initiateVote(g.member1().accessToken(), g.groupId()).andExpect(status().isCreated());

            castVote(g.member2().accessToken(), g.groupId(), "DISAGREE")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.disagreeCount").value(1))
                    .andExpect(jsonPath("$.inProgress").value(true))
                    .andExpect(jsonPath("$.myChoice").value("DISAGREE"));
        }

        @Test
        @DisplayName("이미 투표했으면 409")
        void rejectsDuplicateVote() throws Exception {
            // 5명(n=4): member2가 AGREE 해도 agree(2)*2=4 > 4 → FALSE, 투표 유지
            var g = setupFivePersonActiveGroup();
            initiateVote(g.member1().accessToken(), g.groupId()).andExpect(status().isCreated());
            castVote(g.member2().accessToken(), g.groupId(), "AGREE").andExpect(status().isOk());

            castVote(g.member2().accessToken(), g.groupId(), "DISAGREE")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("KICK_VOTE_ALREADY_VOTED"));
        }

        @Test
        @DisplayName("choice 없이 요청하면 400")
        void rejectsMissingChoice() throws Exception {
            var g = setupFivePersonActiveGroup();
            initiateVote(g.member1().accessToken(), g.groupId()).andExpect(status().isCreated());

            mockMvc.perform(jsonRequest(
                            withToken(
                                    post("/api/groups/" + g.groupId() + "/ownerKickVotes/cast"),
                                    g.member2().accessToken()),
                            "{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("과반수 찬성 달성 → 방장이 즉시 KICKED되고 새 방장이 선정된다")
        void ownerIsKickedWhenMajorityAgrees() throws Exception {
            // 3명(n=2): member1 AGREE(개시), member2 AGREE → agree(2)*2=4 > 2 → 가결
            var g = setupThreePersonActiveGroup();
            Long ownerId = userIdOf(OWNER_EMAIL);
            initiateVote(g.member1().accessToken(), g.groupId()).andExpect(status().isCreated());

            castVote(g.member2().accessToken(), g.groupId(), "AGREE")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.inProgress").value(false));

            assertThat(groupMemberStatusOf(g.groupId(), ownerId)).isEqualTo("KICKED");
            assertThat(ownerIdOf(g.groupId())).isNotEqualTo(ownerId);
            assertThat(kickVoteStartedAtOf(g.groupId())).isNull();
        }

        @Test
        @DisplayName("과반수 반대 달성 → 투표가 종료되고 방장은 유지된다")
        void voteEndsWhenMajorityDisagrees() throws Exception {
            // 3명(n=2): member1 AGREE(개시), member2 DISAGREE → disagree(1)*2=2 >= 2 → 부결
            var g = setupThreePersonActiveGroup();
            Long ownerId = userIdOf(OWNER_EMAIL);
            initiateVote(g.member1().accessToken(), g.groupId()).andExpect(status().isCreated());

            castVote(g.member2().accessToken(), g.groupId(), "DISAGREE")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.inProgress").value(false));

            assertThat(ownerIdOf(g.groupId())).isEqualTo(ownerId);
            assertThat(groupMemberStatusOf(g.groupId(), ownerId)).isEqualTo("ACTIVE");
            assertThat(kickVoteStartedAtOf(g.groupId())).isNull();
        }
    }

    // ── 현황 조회 ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("현황 조회 GET /{groupId}/ownerKickVotes")
    class GetVoteStatus {

        @Test
        @DisplayName("진행 중인 투표가 없으면 404")
        void rejectsWhenNoActiveVote() throws Exception {
            var g = setupFivePersonActiveGroup();

            getVoteStatus(g.member1().accessToken(), g.groupId()).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("투표 현황을 올바르게 반환한다 — 미투표 멤버는 myChoice=NONE")
        void returnsCurrentVoteStatus() throws Exception {
            // 5명(n=4): member1 개시 후 agree=1, 투표 지속
            var g = setupFivePersonActiveGroup();
            Long ownerId = userIdOf(OWNER_EMAIL);
            initiateVote(g.member1().accessToken(), g.groupId()).andExpect(status().isCreated());

            getVoteStatus(g.member2().accessToken(), g.groupId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.inProgress").value(true))
                    .andExpect(jsonPath("$.targetOwnerId").value(ownerId))
                    .andExpect(jsonPath("$.agreeCount").value(1))
                    .andExpect(jsonPath("$.disagreeCount").value(0))
                    .andExpect(jsonPath("$.eligibleVoters").value(4))
                    .andExpect(jsonPath("$.myChoice").value("NONE"))
                    .andExpect(jsonPath("$.startedAt").exists())
                    .andExpect(jsonPath("$.expiresAt").exists());
        }
    }
}
