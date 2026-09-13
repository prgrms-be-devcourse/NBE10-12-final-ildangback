package com.gommit.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gommit.support.IntegrationTestSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

@DisplayName("회원 탈퇴 시 그룹 정리")
class AccountDeletionIntegrationTest extends IntegrationTestSupport {

    private static final String OWNER_EMAIL = "owner@example.com";
    private static final String MEMBER_EMAIL = "member@example.com";

    // ===================================================================
    // 엔드포인트 호출. 공용 기반이 아니라 여기 둔다.
    // ===================================================================

    private ResultActions createGroup(String accessToken, String name) throws Exception {
        return mockMvc.perform(jsonRequest(withToken(post("/api/groups"), accessToken), groupCreateBody(name)));
    }

    private ResultActions joinGroup(String accessToken, Long groupId) throws Exception {
        return mockMvc.perform(withToken(post("/api/groups/" + groupId + "/members"), accessToken));
    }

    private ResultActions deleteAccount(String accessToken) throws Exception {
        return mockMvc.perform(
                jsonRequest(withToken(delete("/api/users/me"), accessToken), json("password", DEFAULT_PASSWORD)));
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

    // ===================================================================
    // DB 조회
    // ===================================================================

    private Long userIdOf(String email) {
        return jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, email);
    }

    private Long groupIdOf(Long ownerId) {
        return jdbcTemplate.queryForObject(
                "select max(id) from challenge_groups where owner_id = ?", Long.class, ownerId);
    }

    private Long challengeIdOf(Long groupId) {
        return jdbcTemplate.queryForObject("select max(id) from challenges where group_id = ?", Long.class, groupId);
    }

    private String groupStatusOf(Long groupId) {
        return jdbcTemplate.queryForObject("select status from challenge_groups where id = ?", String.class, groupId);
    }

    private Long groupOwnerOf(Long groupId) {
        return jdbcTemplate.queryForObject("select owner_id from challenge_groups where id = ?", Long.class, groupId);
    }

    private String challengeStatusOf(Long challengeId) {
        return jdbcTemplate.queryForObject("select status from challenges where id = ?", String.class, challengeId);
    }

    private String groupMemberStatusOf(Long groupId, Long userId) {
        return jdbcTemplate.queryForObject(
                "select status from group_members where group_id = ? and user_id = ?", String.class, groupId, userId);
    }

    private String challengeMemberStatusOf(Long challengeId, Long userId) {
        return jdbcTemplate.queryForObject(
                "select status from challenge_members where challenge_id = ? and user_id = ?",
                String.class,
                challengeId,
                userId);
    }

    private String challengeMemberRoleOf(Long challengeId, Long userId) {
        return jdbcTemplate.queryForObject(
                "select role from challenge_members where challenge_id = ? and user_id = ?",
                String.class,
                challengeId,
                userId);
    }

    // 그룹 하나와 방장, 참여자 한 명을 만든다.
    private Fixture groupWithTwoMembers() throws Exception {
        Tokens owner = loginAs(OWNER_EMAIL, "방장");
        Long ownerId = userIdOf(OWNER_EMAIL);
        createGroup(owner.accessToken(), "꼬밋 그룹").andExpect(status().isCreated());
        Long groupId = groupIdOf(ownerId);

        Tokens member = loginAs(MEMBER_EMAIL, "참여자");
        Long memberId = userIdOf(MEMBER_EMAIL);
        joinGroup(member.accessToken(), groupId).andExpect(status().isCreated());

        return new Fixture(owner, ownerId, member, memberId, groupId, challengeIdOf(groupId));
    }

    private record Fixture(Tokens owner, Long ownerId, Tokens member, Long memberId, Long groupId, Long challengeId) {}

    @Test
    @DisplayName("일반 멤버가 탈퇴하면 그룹과 시즌에서 LEFT 가 되고 방장은 그대로다")
    void deleteAccountLeavesGroupAndChallengeForPlainMember() throws Exception {
        Fixture fixture = groupWithTwoMembers();

        deleteAccount(fixture.member().accessToken()).andExpect(status().isNoContent());

        assertThat(groupMemberStatusOf(fixture.groupId(), fixture.memberId())).isEqualTo("LEFT");
        assertThat(challengeMemberStatusOf(fixture.challengeId(), fixture.memberId()))
                .isEqualTo("LEFT");
        assertThat(groupOwnerOf(fixture.groupId())).isEqualTo(fixture.ownerId());
        assertThat(groupStatusOf(fixture.groupId())).isEqualTo("READY");
    }

    // 그룹 멤버라고 그 시즌 참가자인 것은 아니다. 널 가드를 빼면 탈퇴가 NPE 로 500 이 된다.
    @Test
    @DisplayName("시즌에 참가하지 않은 멤버가 탈퇴해도 그룹만 정리된다")
    void deleteAccountLeavesGroupWhenNotChallengeMember() throws Exception {
        Fixture fixture = groupWithTwoMembers();
        jdbcTemplate.update(
                "update challenge_members set status = 'LEFT' where challenge_id = ? and user_id = ?",
                fixture.challengeId(),
                fixture.memberId());

        deleteAccount(fixture.member().accessToken()).andExpect(status().isNoContent());

        assertThat(groupMemberStatusOf(fixture.groupId(), fixture.memberId())).isEqualTo("LEFT");
        assertThat(challengeMemberStatusOf(fixture.challengeId(), fixture.memberId()))
                .isEqualTo("LEFT");
        assertThat(groupOwnerOf(fixture.groupId())).isEqualTo(fixture.ownerId());
    }

    @Test
    @DisplayName("방장이 탈퇴하면 남은 멤버가 그룹 OWNER 와 시즌 OWNER 를 함께 받는다")
    void deleteAccountHandsOverBothOwnerships() throws Exception {
        Fixture fixture = groupWithTwoMembers();

        deleteAccount(fixture.owner().accessToken()).andExpect(status().isNoContent());

        assertThat(groupOwnerOf(fixture.groupId())).isEqualTo(fixture.memberId());
        assertThat(challengeMemberRoleOf(fixture.challengeId(), fixture.memberId()))
                .isEqualTo("OWNER");
        assertThat(groupStatusOf(fixture.groupId())).isEqualTo("READY");
        assertThat(groupMemberStatusOf(fixture.groupId(), fixture.ownerId())).isEqualTo("LEFT");
    }

    // role 이 OWNER 로 남으면 시즌 활성화 배치가 탈퇴자를 그룹 OWNER 로 앉힌다.
    @Test
    @DisplayName("탈퇴한 방장의 시즌 역할은 MEMBER 로 내려간다")
    void deletedOwnerNoLongerHoldsChallengeOwnerRole() throws Exception {
        Fixture fixture = groupWithTwoMembers();

        deleteAccount(fixture.owner().accessToken()).andExpect(status().isNoContent());

        assertThat(challengeMemberRoleOf(fixture.challengeId(), fixture.ownerId()))
                .isEqualTo("MEMBER");
        assertThat(challengeMemberStatusOf(fixture.challengeId(), fixture.ownerId()))
                .isEqualTo("LEFT");
    }

    @Test
    @DisplayName("진행 중인 시즌에서도 남은 멤버가 OWNER 를 받는다")
    void deleteAccountHandsOverOnActiveChallenge() throws Exception {
        Fixture fixture = groupWithTwoMembers();
        jdbcTemplate.update("update challenges set status = 'ACTIVE' where id = ?", fixture.challengeId());
        jdbcTemplate.update("update challenge_groups set status = 'ACTIVE' where id = ?", fixture.groupId());

        deleteAccount(fixture.owner().accessToken()).andExpect(status().isNoContent());

        assertThat(groupOwnerOf(fixture.groupId())).isEqualTo(fixture.memberId());
        assertThat(challengeMemberRoleOf(fixture.challengeId(), fixture.memberId()))
                .isEqualTo("OWNER");
        assertThat(challengeStatusOf(fixture.challengeId())).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("1인 그룹의 방장이 탈퇴하면 그룹과 시즌이 종료된다")
    void deleteAccountEndsGroupAndChallengeForSoleOwner() throws Exception {
        Tokens owner = loginAs(OWNER_EMAIL, "방장");
        Long ownerId = userIdOf(OWNER_EMAIL);
        createGroup(owner.accessToken(), "혼자 하는 그룹").andExpect(status().isCreated());
        Long groupId = groupIdOf(ownerId);
        Long challengeId = challengeIdOf(groupId);

        deleteAccount(owner.accessToken()).andExpect(status().isNoContent());

        assertThat(groupStatusOf(groupId)).isEqualTo("ENDED");
        assertThat(challengeStatusOf(challengeId)).isEqualTo("ENDED");
        assertThat(groupMemberStatusOf(groupId, ownerId)).isEqualTo("LEFT");
    }

    @Test
    @DisplayName("그룹에 속하지 않은 사용자도 탈퇴할 수 있다")
    void deleteAccountWithoutAnyGroupSucceeds() throws Exception {
        Tokens tokens = loginAs("solo@example.com", "혼자");

        deleteAccount(tokens.accessToken()).andExpect(status().isNoContent());

        Long userId = jdbcTemplate.queryForObject("select id from users where nickname like '탈퇴한사용자_%'", Long.class);
        assertThat(jdbcTemplate.queryForObject("select email from users where id = ?", String.class, userId))
                .isEqualTo("deleted_" + userId + "@example.com");
    }
}
