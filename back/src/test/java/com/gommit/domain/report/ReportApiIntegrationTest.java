package com.gommit.domain.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gommit.domain.report.dto.request.DecideAppealRequest;
import com.gommit.domain.report.dto.request.DecideReportRequest;
import com.gommit.domain.report.dto.request.PenaltyCommand;
import com.gommit.domain.report.entity.PenaltyType;
import com.gommit.domain.report.service.AppealService;
import com.gommit.domain.report.service.ReportService;
import com.gommit.global.exception.BusinessException;
import com.gommit.support.IntegrationTestSupport;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

@DisplayName("신고 및 이의제기 API")
class ReportApiIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private ReportService reportService;

    @Autowired
    private AppealService appealService;

    private static final String REPORTER_EMAIL = "reporter@example.com";
    private static final String REPORTER_NICKNAME = "신고자";
    private static final String TARGET_EMAIL = "target@example.com";
    private static final String TARGET_NICKNAME = "대상자";
    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String ADMIN_NICKNAME = "관리자";
    private static final String OWNER_EMAIL = "owner@example.com";
    private static final String OWNER_NICKNAME = "방장";

    private static final String WARNING = "{\"penaltyType\":\"WARNING\"}";
    private static final String PERMANENT_BAN = "{\"penaltyType\":\"PERMANENT_BAN\"}";

    // ===================================================================
    // report 도메인 엔드포인트 호출. 공용 기반이 아니라 여기 둔다.
    // ===================================================================

    private ResultActions submitReport(String accessToken, String body) throws Exception {
        return mockMvc.perform(jsonRequest(withToken(post("/api/reports"), accessToken), body));
    }

    private ResultActions getReports(String accessToken, String queryString) throws Exception {
        return mockMvc.perform(withToken(get("/api/admin/reports" + queryString), accessToken));
    }

    private ResultActions decideReport(String accessToken, Long reportId, String body) throws Exception {
        return mockMvc.perform(jsonRequest(withToken(patch("/api/admin/reports/" + reportId), accessToken), body));
    }

    private ResultActions penalizeDirectly(String accessToken, String body) throws Exception {
        return mockMvc.perform(jsonRequest(withToken(post("/api/admin/penalties"), accessToken), body));
    }

    private ResultActions submitAppeal(String accessToken, Long reportId, String content) throws Exception {
        return mockMvc.perform(jsonRequest(
                withToken(post("/api/reports/" + reportId + "/appeals"), accessToken), json("content", content)));
    }

    private ResultActions getMyPenalties(String accessToken) throws Exception {
        return mockMvc.perform(withToken(get("/api/penalties/me"), accessToken));
    }

    private ResultActions getAppeals(String accessToken, String queryString) throws Exception {
        return mockMvc.perform(withToken(get("/api/admin/appeals" + queryString), accessToken));
    }

    private ResultActions decideAppeal(String accessToken, Long appealId, boolean accept) throws Exception {
        return mockMvc.perform(jsonRequest(
                withToken(patch("/api/admin/appeals/" + appealId), accessToken), "{\"accept\":%b}".formatted(accept)));
    }

    // 로그인과 사용자 조회

    private ResultActions login(String email) throws Exception {
        return mockMvc.perform(
                jsonRequest(post("/api/auth/login"), json("email", email, "password", DEFAULT_PASSWORD)));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(jsonRequest(post("/api/auth/refresh"), json("refreshToken", refreshToken)));
    }

    private ResultActions getMyProfile(String accessToken) throws Exception {
        return mockMvc.perform(withToken(get("/api/users/me"), accessToken));
    }

    private ResultActions updateIntroduction(String accessToken, String introduction) throws Exception {
        return mockMvc.perform(
                        jsonRequest(withToken(patch("/api/users/me"), accessToken), json("introduction", introduction)))
                .andExpect(status().isOk());
    }

    // 공용 로그인 헬퍼는 일반 사용자만 만든다. 역할을 바꾸고 다시 로그인해야 관리자 토큰이 나온다
    private Tokens loginAsAdmin() {
        loginAs(ADMIN_EMAIL, ADMIN_NICKNAME);
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE email = ?", ADMIN_EMAIL);
        return loginAs(ADMIN_EMAIL, ADMIN_NICKNAME);
    }

    private Long userIdOf(String email) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    // 그룹과 인증 준비

    private ResultActions joinGroup(String accessToken, Long groupId) throws Exception {
        return mockMvc.perform(withToken(post("/api/groups/" + groupId + "/members"), accessToken));
    }

    private ResultActions getGroupDetail(String accessToken, Long groupId) throws Exception {
        return mockMvc.perform(withToken(get("/api/groups/" + groupId), accessToken));
    }

    private Long createGroupAndReturnId(String accessToken, Long ownerId) throws Exception {
        String body = """
                {
                  "name": "함께 달리기",
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
                """.formatted(LocalDate.now().plusDays(1), LocalDate.now().plusDays(7));
        mockMvc.perform(jsonRequest(withToken(post("/api/groups"), accessToken), body))
                .andExpect(status().isCreated());
        return jdbcTemplate.queryForObject(
                "SELECT id FROM challenge_groups WHERE owner_id = ? ORDER BY id DESC LIMIT 1", Long.class, ownerId);
    }

    private Long challengeIdOf(Long groupId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM challenges WHERE group_id = ? ORDER BY id DESC LIMIT 1", Long.class, groupId);
    }

    // 인증 제출은 멀티파트라, 판정 대상만 필요한 테스트에서는 행을 직접 넣는다
    private Long insertCheckIn(Long challengeId, Long userId, String mediaKey, String memo) {
        jdbcTemplate.update(
                "INSERT INTO check_ins"
                        + " (challenge_id, user_id, round_no, check_in_type, media_key, media_type, memo,"
                        + " business_date, created_at, updated_at)"
                        + " VALUES (?, ?, 1, 'PHOTO', ?, 'IMAGE', ?, ?, NOW(6), NOW(6))",
                challengeId,
                userId,
                mediaKey,
                memo,
                LocalDate.now());
        return jdbcTemplate.queryForObject(
                "SELECT id FROM check_ins WHERE challenge_id = ? AND user_id = ?", Long.class, challengeId, userId);
    }

    // 채팅은 STOMP 로만 보낼 수 있어서, 신고 대상만 필요한 테스트에서는 행을 직접 넣는다
    private Long insertMessage(Long groupId, Long senderId, String messageType, String content) {
        jdbcTemplate.update(
                "INSERT INTO group_messages (group_id, sender_id, message_type, content, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, NOW(6), NOW(6))",
                groupId,
                senderId,
                messageType,
                content);
        return jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM group_messages WHERE group_id = ?", Long.class, groupId);
    }

    private String messageContentOf(Long messageId) {
        return jdbcTemplate.queryForObject("SELECT content FROM group_messages WHERE id = ?", String.class, messageId);
    }

    private String messageTypeOf(Long messageId) {
        return jdbcTemplate.queryForObject(
                "SELECT message_type FROM group_messages WHERE id = ?", String.class, messageId);
    }

    private void givePoints(Long userId, int amount) {
        jdbcTemplate.update(
                "INSERT INTO user_points (user_id, balance, created_at, updated_at)" + " VALUES (?, ?, NOW(6), NOW(6))",
                userId,
                amount);
    }

    private int balanceOf(Long userId) {
        Integer balance =
                jdbcTemplate.queryForObject("SELECT balance FROM user_points WHERE user_id = ?", Integer.class, userId);
        return balance == null ? 0 : balance;
    }

    private List<Map<String, Object>> pointHistoriesOf(Long userId) {
        return jdbcTemplate.queryForList(
                "SELECT reason, amount, balance_after FROM user_point_histories WHERE user_id = ? ORDER BY id", userId);
    }

    private Long ownerIdOf(Long groupId) {
        return jdbcTemplate.queryForObject("SELECT owner_id FROM challenge_groups WHERE id = ?", Long.class, groupId);
    }

    private String statusOfGroup(Long groupId) {
        return jdbcTemplate.queryForObject("SELECT status FROM challenge_groups WHERE id = ?", String.class, groupId);
    }

    private long activePenaltyCountOf(Long reportId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_penalties WHERE report_id = ? AND revoked_at IS NULL", Long.class, reportId);
    }

    // 요청 본문 조립과 응답 읽기

    private static String bodyOf(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }

    private static String reportBody(String targetType, Long targetId, String reason, String detail) {
        return """
            {"targetType":"%s","targetId":%d,"reason":"%s","detail":%s}
            """.formatted(targetType, targetId, reason, detail == null ? "null" : "\"" + detail + "\"");
    }

    private static String decideBody(boolean accept, String... penalties) {
        return """
            {"accept":%b,"penalties":[%s]}
            """.formatted(accept, String.join(",", penalties));
    }

    private static String directPenaltyBody(String email, String... penalties) {
        return """
            {"email":"%s","detail":"반복 신고 누적으로 직권 제재","penalties":[%s]}
            """.formatted(email, String.join(",", penalties));
    }

    private static String suspension(int days) {
        return "{\"penaltyType\":\"SUSPENSION\",\"suspensionDays\":%d}".formatted(days);
    }

    private static String pointForfeit(int amount) {
        return "{\"penaltyType\":\"POINT_FORFEIT\",\"amount\":%d}".formatted(amount);
    }

    // 여러 테스트가 공유하는 흐름

    private Long submitReportAndGetId(String accessToken, Long targetUserId, String reason) throws Exception {
        return submitReportAndGetId(accessToken, "USER", targetUserId, reason);
    }

    private Long submitReportAndGetId(String accessToken, String targetType, Long targetId, String reason)
            throws Exception {
        String body = bodyOf(submitReport(accessToken, reportBody(targetType, targetId, reason, null))
                .andExpect(status().isCreated()));
        return Long.parseLong(fieldOf(body, "id"));
    }

    private Long submitAppealAndGetId(String accessToken, Long reportId) throws Exception {
        String body = bodyOf(submitAppeal(accessToken, reportId, "억울합니다").andExpect(status().isCreated()));
        return Long.parseLong(fieldOf(body, "id"));
    }

    // 신고자와 관리자를 만들고 대상자를 신고해 승인까지 끝낸다. 대상자는 미리 가입되어 있어야 한다
    private Long acceptWith(String... penalties) throws Exception {
        var reporter = loginAs(REPORTER_EMAIL, REPORTER_NICKNAME);
        loginAs(TARGET_EMAIL, TARGET_NICKNAME);
        var admin = loginAsAdmin();
        Long reportId = submitReportAndGetId(reporter.accessToken(), userIdOf(TARGET_EMAIL), "ABUSE");
        decideReport(admin.accessToken(), reportId, decideBody(true, penalties)).andExpect(status().isOk());
        return reportId;
    }

    // 여러 스레드가 같은 판정을 동시에 부른다. 성공한 횟수를 돌려준다
    private int decideConcurrently(int threadCount, Runnable decide) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        List<Callable<Boolean>> tasks = IntStream.range(0, threadCount)
                .<Callable<Boolean>>mapToObj(i -> () -> {
                    try {
                        decide.run();
                        return true;
                    } catch (BusinessException e) {
                        return false;
                    }
                })
                .toList();
        List<Future<Boolean>> futures = pool.invokeAll(tasks);
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);
        int succeeded = 0;
        for (Future<Boolean> future : futures) {
            if (future.get()) {
                succeeded++;
            }
        }
        return succeeded;
    }

    @Nested
    @DisplayName("동시 판정")
    class ConcurrentDecision {

        @Test
        @DisplayName("같은 신고를 동시에 승인해도 제재와 압수는 한 번만 일어난다")
        void concurrentReportDecisionAppliesPenaltyOnce() throws Exception {
            var reporter = loginAs(REPORTER_EMAIL, REPORTER_NICKNAME);
            loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            Long targetId = userIdOf(TARGET_EMAIL);
            givePoints(targetId, 500);
            var admin = loginAsAdmin();
            Long adminId = userIdOf(ADMIN_EMAIL);
            Long reportId = submitReportAndGetId(reporter.accessToken(), targetId, "FAKE");
            DecideReportRequest request =
                    new DecideReportRequest(true, List.of(new PenaltyCommand(PenaltyType.POINT_FORFEIT, null, 200)));

            int succeeded = decideConcurrently(8, () -> reportService.decide(adminId, reportId, request));

            assertThat(succeeded).isEqualTo(1);
            assertThat(activePenaltyCountOf(reportId)).isEqualTo(1);
            assertThat(balanceOf(targetId)).isEqualTo(300);
        }

        @Test
        @DisplayName("같은 이의제기를 동시에 인용해도 압수 포인트는 한 번만 환급된다")
        void concurrentAppealDecisionRefundsOnce() throws Exception {
            var target = loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            Long targetId = userIdOf(TARGET_EMAIL);
            givePoints(targetId, 500);
            Long reportId = acceptWith(pointForfeit(200));
            Long appealId = submitAppealAndGetId(target.accessToken(), reportId);
            Long adminId = userIdOf(ADMIN_EMAIL);
            DecideAppealRequest request = new DecideAppealRequest(true);

            int succeeded = decideConcurrently(8, () -> appealService.decide(adminId, appealId, request));

            assertThat(succeeded).isEqualTo(1);
            assertThat(balanceOf(targetId)).isEqualTo(500);
            assertThat(pointHistoriesOf(targetId)).hasSize(2);
        }
    }

    @Nested
    @DisplayName("신고 접수")
    class SubmitReport {

        @Test
        @DisplayName("신고를 접수하면 201 이고 관리자 목록에 나타난다")
        void submittedReportAppearsInAdminList() throws Exception {
            var reporter = loginAs(REPORTER_EMAIL, REPORTER_NICKNAME);
            loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            Long targetId = userIdOf(TARGET_EMAIL);
            var admin = loginAsAdmin();

            submitReport(reporter.accessToken(), reportBody("USER", targetId, "ABUSE", "욕설을 했습니다"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.targetType").value("USER"))
                    .andExpect(jsonPath("$.targetId").value(targetId));

            getReports(admin.accessToken(), "?status=PENDING")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].reason").value("ABUSE"))
                    .andExpect(jsonPath("$.content[0].targetUserId").value(targetId))
                    .andExpect(jsonPath("$.content[0].targetUserNickname").value(TARGET_NICKNAME))
                    .andExpect(jsonPath("$.content[0].reporterNickname").value(REPORTER_NICKNAME))
                    .andExpect(jsonPath("$.content[0].detail").value("욕설을 했습니다"))
                    .andExpect(jsonPath("$.content[0].penalties.length()").value(0));
        }

        @Test
        @DisplayName("접수 시점의 닉네임과 자기소개가 원본으로 남는다")
        void keepsReportedContentAtSubmission() throws Exception {
            var reporter = loginAs(REPORTER_EMAIL, REPORTER_NICKNAME);
            var target = loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            updateIntroduction(target.accessToken(), "문제가 되는 자기소개");
            Long targetId = userIdOf(TARGET_EMAIL);
            var admin = loginAsAdmin();

            submitReport(reporter.accessToken(), reportBody("USER", targetId, "ABUSE", null))
                    .andExpect(status().isCreated());
            updateIntroduction(target.accessToken(), "멀쩡한 자기소개");

            getReports(admin.accessToken(), "")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].reportedContent").value("닉네임: 대상자 | 자기소개: 문제가 되는 자기소개"));
        }

        @Test
        @DisplayName("처리 대기 중인 신고가 있는 대상에 같은 사람이 또 내면 409")
        void rejectsDuplicatePendingReport() throws Exception {
            var reporter = loginAs(REPORTER_EMAIL, REPORTER_NICKNAME);
            loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            Long targetId = userIdOf(TARGET_EMAIL);

            submitReport(reporter.accessToken(), reportBody("USER", targetId, "ABUSE", null))
                    .andExpect(status().isCreated());

            submitReport(reporter.accessToken(), reportBody("USER", targetId, "SPAM", null))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DUPLICATE_PENDING_REPORT"));
        }

        @Test
        @DisplayName("처리가 끝난 대상은 같은 사람이 다시 신고할 수 있다")
        void allowsReportAgainAfterDecided() throws Exception {
            var reporter = loginAs(REPORTER_EMAIL, REPORTER_NICKNAME);
            loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            Long targetId = userIdOf(TARGET_EMAIL);
            var admin = loginAsAdmin();

            Long reportId = submitReportAndGetId(reporter.accessToken(), targetId, "ABUSE");
            decideReport(admin.accessToken(), reportId, decideBody(false)).andExpect(status().isOk());

            submitReport(reporter.accessToken(), reportBody("USER", targetId, "ABUSE", null))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("기타 사유에 상황 설명이 없으면 400")
        void rejectsEtcWithoutDetail() throws Exception {
            var reporter = loginAs(REPORTER_EMAIL, REPORTER_NICKNAME);
            loginAs(TARGET_EMAIL, TARGET_NICKNAME);

            submitReport(reporter.accessToken(), reportBody("USER", userIdOf(TARGET_EMAIL), "ETC", null))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("REPORT_DETAIL_REQUIRED"));
        }

        @Test
        @DisplayName("기타 사유에 상황 설명을 붙이면 접수된다")
        void acceptsEtcWithDetail() throws Exception {
            var reporter = loginAs(REPORTER_EMAIL, REPORTER_NICKNAME);
            loginAs(TARGET_EMAIL, TARGET_NICKNAME);

            submitReport(reporter.accessToken(), reportBody("USER", userIdOf(TARGET_EMAIL), "ETC", "목록에 없는 사유입니다"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.reason").value("ETC"));
        }

        @Test
        @DisplayName("기타 사유에 공백만 보내면 400")
        void rejectsEtcWithBlankDetail() throws Exception {
            var reporter = loginAs(REPORTER_EMAIL, REPORTER_NICKNAME);
            loginAs(TARGET_EMAIL, TARGET_NICKNAME);

            submitReport(reporter.accessToken(), reportBody("USER", userIdOf(TARGET_EMAIL), "ETC", "   "))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("REPORT_DETAIL_REQUIRED"));
        }

        @Test
        @DisplayName("자기 자신은 신고할 수 없다")
        void rejectsSelfReport() throws Exception {
            var reporter = loginAs(REPORTER_EMAIL, REPORTER_NICKNAME);

            submitReport(reporter.accessToken(), reportBody("USER", userIdOf(REPORTER_EMAIL), "ABUSE", null))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SELF_REPORT_NOT_ALLOWED"));
        }

        @Test
        @DisplayName("없는 메시지를 신고하면 404")
        void rejectsMissingChatMessage() throws Exception {
            var reporter = loginAs(REPORTER_EMAIL, REPORTER_NICKNAME);

            submitReport(reporter.accessToken(), reportBody("CHAT_MESSAGE", 999_999L, "ABUSE", null))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CHAT_MESSAGE_NOT_FOUND"));
        }

        @Test
        @DisplayName("보낸 사람이 없는 시스템 메시지는 신고할 수 없다")
        void rejectsSystemChatMessage() throws Exception {
            var reporter = loginAs(REPORTER_EMAIL, REPORTER_NICKNAME);
            var target = loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            Long groupId = createGroupAndReturnId(target.accessToken(), userIdOf(TARGET_EMAIL));
            Long messageId = insertMessage(groupId, null, "SYSTEM", "그룹이 만들어졌어요");

            submitReport(reporter.accessToken(), reportBody("CHAT_MESSAGE", messageId, "ABUSE", null))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CHAT_MESSAGE_NOT_FOUND"));
        }

        @Test
        @DisplayName("없는 사용자를 신고하면 404")
        void rejectsMissingUserTarget() throws Exception {
            var reporter = loginAs(REPORTER_EMAIL, REPORTER_NICKNAME);

            submitReport(reporter.accessToken(), reportBody("USER", 999_999L, "ABUSE", null))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        }

        @Test
        @DisplayName("로그인하지 않으면 신고할 수 없다")
        void rejectsAnonymousReport() throws Exception {
            loginAs(TARGET_EMAIL, TARGET_NICKNAME);

            mockMvc.perform(jsonRequest(
                            post("/api/reports"), reportBody("USER", userIdOf(TARGET_EMAIL), "ABUSE", null)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("신고 판정과 콘텐츠 조치")
    class DecideReport {

        @Test
        @DisplayName("승인하면 닉네임이 초기화되고 자기소개가 비워진다")
        void acceptResetsNicknameAndIntroduction() throws Exception {
            var reporter = loginAs(REPORTER_EMAIL, REPORTER_NICKNAME);
            var target = loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            updateIntroduction(target.accessToken(), "문제가 되는 자기소개");
            Long targetId = userIdOf(TARGET_EMAIL);
            var admin = loginAsAdmin();

            Long reportId = submitReportAndGetId(reporter.accessToken(), targetId, "ABUSE");
            decideReport(admin.accessToken(), reportId, decideBody(true, WARNING))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ACCEPTED"))
                    .andExpect(jsonPath("$.decidedBy").value(userIdOf(ADMIN_EMAIL)))
                    .andExpect(jsonPath("$.penalties[0].penaltyType").value("WARNING"));

            getMyProfile(target.accessToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nickname").value("제재된사용자_" + targetId))
                    .andExpect(jsonPath("$.introduction").doesNotExist());
        }

        @Test
        @DisplayName("기각하면 닉네임이 그대로다")
        void rejectKeepsProfile() throws Exception {
            var reporter = loginAs(REPORTER_EMAIL, REPORTER_NICKNAME);
            var target = loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            var admin = loginAsAdmin();

            Long reportId = submitReportAndGetId(reporter.accessToken(), userIdOf(TARGET_EMAIL), "ABUSE");
            decideReport(admin.accessToken(), reportId, decideBody(false))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("REJECTED"))
                    .andExpect(jsonPath("$.penalties.length()").value(0));

            getMyProfile(target.accessToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nickname").value(TARGET_NICKNAME));
        }

        @Test
        @DisplayName("이미 판정이 끝난 신고는 다시 판정할 수 없다")
        void rejectsSecondDecision() throws Exception {
            var reporter = loginAs(REPORTER_EMAIL, REPORTER_NICKNAME);
            loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            var admin = loginAsAdmin();

            Long reportId = submitReportAndGetId(reporter.accessToken(), userIdOf(TARGET_EMAIL), "ABUSE");
            decideReport(admin.accessToken(), reportId, decideBody(false)).andExpect(status().isOk());

            decideReport(admin.accessToken(), reportId, decideBody(true, WARNING))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("REPORT_ALREADY_DECIDED"));
        }

        @Test
        @DisplayName("없는 신고를 판정하면 404")
        void rejectsDecisionOnMissingReport() throws Exception {
            var admin = loginAsAdmin();

            decideReport(admin.accessToken(), 999_999L, decideBody(true, WARNING))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
        }

        @Test
        @DisplayName("신고가 하나도 없으면 빈 목록을 준다")
        void returnsEmptyReportList() throws Exception {
            var admin = loginAsAdmin();

            getReports(admin.accessToken(), "")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(0))
                    .andExpect(jsonPath("$.hasNext").value(false));
        }

        @Test
        @DisplayName("제재를 하나도 고르지 않고 승인하면 400")
        void rejectsAcceptWithoutPenalty() throws Exception {
            var reporter = loginAs(REPORTER_EMAIL, REPORTER_NICKNAME);
            loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            var admin = loginAsAdmin();

            Long reportId = submitReportAndGetId(reporter.accessToken(), userIdOf(TARGET_EMAIL), "ABUSE");

            decideReport(admin.accessToken(), reportId, decideBody(true))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("PENALTY_REQUIRED"));
        }

        @Test
        @DisplayName("기간 정지 일수가 범위를 벗어나면 400")
        void rejectsSuspensionWithInvalidDays() throws Exception {
            var reporter = loginAs(REPORTER_EMAIL, REPORTER_NICKNAME);
            loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            var admin = loginAsAdmin();

            Long reportId = submitReportAndGetId(reporter.accessToken(), userIdOf(TARGET_EMAIL), "ABUSE");

            decideReport(admin.accessToken(), reportId, decideBody(true, "{\"penaltyType\":\"SUSPENSION\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_SUSPENSION_DAYS"));
            decideReport(admin.accessToken(), reportId, decideBody(true, suspension(366)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_SUSPENSION_DAYS"));
        }

        @Test
        @DisplayName("인증을 신고해 승인하면 인증이 지워진다")
        void acceptDeletesCheckIn() throws Exception {
            var reporter = loginAs(REPORTER_EMAIL, REPORTER_NICKNAME);
            var target = loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            Long targetId = userIdOf(TARGET_EMAIL);
            Long groupId = createGroupAndReturnId(target.accessToken(), targetId);
            Long checkInId = insertCheckIn(challengeIdOf(groupId), targetId, "check-ins/2026/09/abc.jpg", "메모");
            var admin = loginAsAdmin();

            Long reportId = submitReportAndGetId(reporter.accessToken(), "CHECK_IN", checkInId, "SEXUAL");
            decideReport(admin.accessToken(), reportId, decideBody(true, WARNING))
                    .andExpect(status().isOk());

            submitReport(reporter.accessToken(), reportBody("CHECK_IN", checkInId, "SEXUAL", null))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CHECK_IN_NOT_FOUND"));
            getReports(admin.accessToken(), "")
                    .andExpect(
                            jsonPath("$.content[0].reportedContent").value("미디어: check-ins/2026/09/abc.jpg | 메모: 메모"));
        }

        @Test
        @DisplayName("채팅 메시지를 신고해 승인하면 내용이 치워진다")
        void acceptHidesChatMessage() throws Exception {
            var reporter = loginAs(REPORTER_EMAIL, REPORTER_NICKNAME);
            var target = loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            Long targetId = userIdOf(TARGET_EMAIL);
            Long groupId = createGroupAndReturnId(target.accessToken(), targetId);
            Long messageId = insertMessage(groupId, targetId, "TEXT", "심한 말");
            var admin = loginAsAdmin();

            Long reportId = submitReportAndGetId(reporter.accessToken(), "CHAT_MESSAGE", messageId, "ABUSE");
            decideReport(admin.accessToken(), reportId, decideBody(true, WARNING))
                    .andExpect(status().isOk());

            assertThat(messageContentOf(messageId)).isEqualTo("관리자가 삭제한 메시지입니다");
            assertThat(messageTypeOf(messageId)).isEqualTo("SYSTEM");
            // 원문은 접수 시점 스냅샷에만 남는다
            getReports(admin.accessToken(), "")
                    .andExpect(jsonPath("$.content[0].reportedContent").value("메시지: 심한 말"));
        }

        @Test
        @DisplayName("포인트 압수는 실제로 잔액을 깎는다")
        void forfeitDeductsBalance() throws Exception {
            var reporter = loginAs(REPORTER_EMAIL, REPORTER_NICKNAME);
            loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            Long targetId = userIdOf(TARGET_EMAIL);
            givePoints(targetId, 500);
            var admin = loginAsAdmin();

            Long reportId = submitReportAndGetId(reporter.accessToken(), targetId, "FAKE");
            decideReport(admin.accessToken(), reportId, decideBody(true, pointForfeit(200)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.penalties[0].amount").value(200));

            assertThat(balanceOf(targetId)).isEqualTo(300);
        }

        @Test
        @DisplayName("잔액보다 큰 금액은 잔액까지만 깎고 그 값을 기록한다")
        void forfeitCapsAtBalance() throws Exception {
            var reporter = loginAs(REPORTER_EMAIL, REPORTER_NICKNAME);
            loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            Long targetId = userIdOf(TARGET_EMAIL);
            givePoints(targetId, 50);
            var admin = loginAsAdmin();

            Long reportId = submitReportAndGetId(reporter.accessToken(), targetId, "FAKE");
            decideReport(admin.accessToken(), reportId, decideBody(true, pointForfeit(200)))
                    .andExpect(status().isOk())
                    // 요청은 200 이었지만 실제로 깎인 50 이 남는다. 복구는 이 값을 봐야 한다
                    .andExpect(jsonPath("$.penalties[0].amount").value(50));

            assertThat(balanceOf(targetId)).isZero();
        }

        @Test
        @DisplayName("잔액이 없어도 압수 판정은 성공한다")
        void forfeitSucceedsWithZeroBalance() throws Exception {
            var reporter = loginAs(REPORTER_EMAIL, REPORTER_NICKNAME);
            loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            Long targetId = userIdOf(TARGET_EMAIL);
            var admin = loginAsAdmin();

            Long reportId = submitReportAndGetId(reporter.accessToken(), targetId, "FAKE");
            decideReport(admin.accessToken(), reportId, decideBody(true, pointForfeit(200)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.penalties[0].amount").value(0));
        }

        @Test
        @DisplayName("일반 사용자가 관리자 경로를 호출하면 403")
        void rejectsNonAdmin() throws Exception {
            var reporter = loginAs(REPORTER_EMAIL, REPORTER_NICKNAME);
            loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            Long reportId = submitReportAndGetId(reporter.accessToken(), userIdOf(TARGET_EMAIL), "ABUSE");

            getReports(reporter.accessToken(), "").andExpect(status().isForbidden());
            getAppeals(reporter.accessToken(), "").andExpect(status().isForbidden());
            decideReport(reporter.accessToken(), reportId, decideBody(true, WARNING))
                    .andExpect(status().isForbidden());
            penalizeDirectly(reporter.accessToken(), directPenaltyBody(TARGET_EMAIL, WARNING))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("제재와 로그인 차단")
    class PenaltyBlocksLogin {

        @Test
        @DisplayName("기간 정지를 받으면 로그인이 거절된다")
        void suspensionBlocksLogin() throws Exception {
            acceptWith(suspension(3));

            login(TARGET_EMAIL)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
        }

        @Test
        @DisplayName("영구 정지를 받으면 로그인이 거절된다")
        void permanentBanBlocksLogin() throws Exception {
            acceptWith(PERMANENT_BAN);

            login(TARGET_EMAIL)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_BANNED"));
        }

        @Test
        @DisplayName("경고만 받으면 로그인이 막히지 않는다")
        void warningDoesNotBlockLogin() throws Exception {
            acceptWith(WARNING);

            login(TARGET_EMAIL).andExpect(status().isOk());
        }

        @Test
        @DisplayName("정지되면 기존 세션의 토큰 재발급이 거절된다")
        void suspensionRevokesExistingSession() throws Exception {
            var target = loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            acceptWith(suspension(3));

            refresh(target.refreshToken())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
        }

        @Test
        @DisplayName("폐기를 피한 RT 로 재발급해도 정지가 막는다")
        void suspensionBlocksRotationThatEscapedRevocation() throws Exception {
            var target = loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            acceptWith(suspension(3));
            // 제재 직전에 발급돼 일괄 폐기를 피한 RT 를 흉내낸다. 로테이션 자체는 성공하므로 발급 직전 검사가 유일한 방어다
            jdbcTemplate.update(
                    "UPDATE refresh_tokens SET revoked_at = NULL, rotated_at = NULL WHERE user_id = ?",
                    userIdOf(TARGET_EMAIL));

            refresh(target.refreshToken())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
        }

        @Test
        @DisplayName("종료 시각이 지난 정지는 로그인을 막지 않는다")
        void expiredSuspensionDoesNotBlockLogin() throws Exception {
            acceptWith(suspension(3));
            jdbcTemplate.update(
                    "UPDATE user_penalties SET ends_at = ? WHERE user_id = ?",
                    LocalDateTime.now().minusDays(1),
                    userIdOf(TARGET_EMAIL));

            login(TARGET_EMAIL).andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("영구 정지와 그룹 정리")
    class PermanentBanCleansUpGroups {

        @Test
        @DisplayName("영구 정지되면 그룹 멤버에서 빠진다")
        void banRemovesMemberFromGroup() throws Exception {
            var owner = loginAs(OWNER_EMAIL, OWNER_NICKNAME);
            Long groupId = createGroupAndReturnId(owner.accessToken(), userIdOf(OWNER_EMAIL));
            var target = loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            joinGroup(target.accessToken(), groupId).andExpect(status().isCreated());

            acceptWith(PERMANENT_BAN);

            getGroupDetail(owner.accessToken(), groupId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.members.length()").value(1))
                    .andExpect(jsonPath("$.members[0].nickname").value(OWNER_NICKNAME));
        }

        @Test
        @DisplayName("기간 정지는 그룹 멤버를 그대로 둔다")
        void suspensionKeepsGroupMembership() throws Exception {
            var owner = loginAs(OWNER_EMAIL, OWNER_NICKNAME);
            Long groupId = createGroupAndReturnId(owner.accessToken(), userIdOf(OWNER_EMAIL));
            var target = loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            joinGroup(target.accessToken(), groupId).andExpect(status().isCreated());

            acceptWith(suspension(3));

            getGroupDetail(owner.accessToken(), groupId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.members.length()").value(2));
        }

        @Test
        @DisplayName("방장이 영구 정지되면 남은 멤버가 방장을 이어받는다")
        void banDelegatesOwnershipToRemainingMember() throws Exception {
            var target = loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            Long groupId = createGroupAndReturnId(target.accessToken(), userIdOf(TARGET_EMAIL));
            var first = loginAs("first@example.com", "남은멤버1");
            joinGroup(first.accessToken(), groupId).andExpect(status().isCreated());
            var second = loginAs("second@example.com", "남은멤버2");
            joinGroup(second.accessToken(), groupId).andExpect(status().isCreated());

            acceptWith(PERMANENT_BAN);

            assertThat(ownerIdOf(groupId)).isIn(userIdOf("first@example.com"), userIdOf("second@example.com"));
            getGroupDetail(first.accessToken(), groupId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.members.length()").value(2));
        }

        @Test
        @DisplayName("혼자인 그룹의 방장이 영구 정지되면 그룹이 종료된다")
        void banEndsGroupWithNoRemainingMember() throws Exception {
            var target = loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            Long groupId = createGroupAndReturnId(target.accessToken(), userIdOf(TARGET_EMAIL));

            acceptWith(PERMANENT_BAN);

            assertThat(statusOfGroup(groupId)).isEqualTo("ENDED");
        }
    }

    @Nested
    @DisplayName("내 제재 목록")
    class MyPenalties {

        @Test
        @DisplayName("제재를 받으면 목록에 나오고 이의제기를 낼 수 있다고 알려준다")
        void showsPenaltyAndAppealable() throws Exception {
            var target = loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            Long reportId = acceptWith(suspension(3), pointForfeit(100));

            getMyPenalties(target.accessToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].reportId").value(reportId))
                    .andExpect(jsonPath("$.content[0].reason").value("ABUSE"))
                    .andExpect(jsonPath("$.content[0].penalties.length()").value(2))
                    .andExpect(jsonPath("$.content[0].appealable").value(true))
                    .andExpect(jsonPath("$.content[0].appeal").doesNotExist());
        }

        @Test
        @DisplayName("이의제기를 내면 더 낼 수 없다고 알려준다")
        void marksNotAppealableOnceAppealed() throws Exception {
            var target = loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            Long reportId = acceptWith(suspension(3));
            submitAppeal(target.accessToken(), reportId, "억울합니다").andExpect(status().isCreated());

            getMyPenalties(target.accessToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].appealable").value(false))
                    .andExpect(jsonPath("$.content[0].appeal.status").value("PENDING"))
                    .andExpect(jsonPath("$.content[0].appeal.content").value("억울합니다"));
        }

        @Test
        @DisplayName("남의 제재는 보이지 않는다")
        void hidesOtherUsersPenalties() throws Exception {
            var reporter = loginAs(REPORTER_EMAIL, REPORTER_NICKNAME);
            acceptWith(suspension(3));

            getMyPenalties(reporter.accessToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(0));
        }

        @Test
        @DisplayName("기각된 신고는 제재가 없어 목록에 없다")
        void hidesRejectedReports() throws Exception {
            var reporter = loginAs(REPORTER_EMAIL, REPORTER_NICKNAME);
            var target = loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            var admin = loginAsAdmin();
            Long reportId = submitReportAndGetId(reporter.accessToken(), userIdOf(TARGET_EMAIL), "ABUSE");
            decideReport(admin.accessToken(), reportId, decideBody(false)).andExpect(status().isOk());

            getMyPenalties(target.accessToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(0));
        }

        @Test
        @DisplayName("신고자가 누구인지는 알려주지 않는다")
        void doesNotExposeReporter() throws Exception {
            var target = loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            acceptWith(WARNING);

            getMyPenalties(target.accessToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].reporterId").doesNotExist())
                    .andExpect(jsonPath("$.content[0].reportedContent").doesNotExist());
        }

        @Test
        @DisplayName("로그인하지 않으면 볼 수 없다")
        void rejectsAnonymous() throws Exception {
            mockMvc.perform(get("/api/penalties/me")).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("이의제기")
    class Appeals {

        @Test
        @DisplayName("승인된 신고에 본인이 이의제기하면 201")
        void submitsAppealOnAcceptedReport() throws Exception {
            var target = loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            Long reportId = acceptWith(suspension(3));

            submitAppeal(target.accessToken(), reportId, "제 계정이 아니었습니다")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.reportId").value(reportId))
                    .andExpect(jsonPath("$.content").value("제 계정이 아니었습니다"));
        }

        @Test
        @DisplayName("기각된 신고에는 이의제기할 수 없다")
        void rejectsAppealOnRejectedReport() throws Exception {
            var reporter = loginAs(REPORTER_EMAIL, REPORTER_NICKNAME);
            var target = loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            var admin = loginAsAdmin();
            Long reportId = submitReportAndGetId(reporter.accessToken(), userIdOf(TARGET_EMAIL), "ABUSE");
            decideReport(admin.accessToken(), reportId, decideBody(false)).andExpect(status().isOk());

            submitAppeal(target.accessToken(), reportId, "억울합니다")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("APPEAL_NOT_ALLOWED"));
        }

        @Test
        @DisplayName("없는 신고에 이의제기하면 404")
        void rejectsAppealOnMissingReport() throws Exception {
            var target = loginAs(TARGET_EMAIL, TARGET_NICKNAME);

            submitAppeal(target.accessToken(), 999_999L, "억울합니다")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
        }

        @Test
        @DisplayName("이의제기가 하나도 없으면 빈 목록을 준다")
        void returnsEmptyAppealList() throws Exception {
            var admin = loginAsAdmin();

            getAppeals(admin.accessToken(), "")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(0))
                    .andExpect(jsonPath("$.hasNext").value(false));
        }

        @Test
        @DisplayName("제재를 받은 본인이 아니면 이의제기할 수 없다")
        void rejectsAppealFromAnotherUser() throws Exception {
            var reporter = loginAs(REPORTER_EMAIL, REPORTER_NICKNAME);
            Long reportId = acceptWith(suspension(3));

            submitAppeal(reporter.accessToken(), reportId, "제가 대신 냅니다")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("APPEAL_NOT_ALLOWED"));
        }

        @Test
        @DisplayName("같은 신고에 두 번 이의제기하면 409")
        void rejectsDuplicateAppeal() throws Exception {
            var target = loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            Long reportId = acceptWith(suspension(3));
            submitAppeal(target.accessToken(), reportId, "억울합니다").andExpect(status().isCreated());

            submitAppeal(target.accessToken(), reportId, "다시 한 번 봐주세요")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("APPEAL_ALREADY_EXISTS"));
        }

        @Test
        @DisplayName("인용하면 정지가 풀려 다시 로그인된다")
        void acceptRestoresLogin() throws Exception {
            var target = loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            Long reportId = acceptWith(suspension(3));
            Long appealId = submitAppealAndGetId(target.accessToken(), reportId);
            var admin = loginAsAdmin();
            login(TARGET_EMAIL).andExpect(status().isForbidden());

            decideAppeal(admin.accessToken(), appealId, true)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ACCEPTED"))
                    .andExpect(jsonPath("$.report.id").value(reportId))
                    .andExpect(jsonPath("$.report.penalties[0].revokedAt").isNotEmpty());

            login(TARGET_EMAIL).andExpect(status().isOk());
        }

        @Test
        @DisplayName("인용하면 정지와 포인트 압수가 한 번에 해제된다")
        void acceptRevokesEveryPenaltyOnTheReport() throws Exception {
            var target = loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            Long reportId = acceptWith(suspension(7), pointForfeit(100));
            Long appealId = submitAppealAndGetId(target.accessToken(), reportId);
            var admin = loginAsAdmin();

            decideAppeal(admin.accessToken(), appealId, true)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.report.penalties.length()").value(2));

            assertThat(activePenaltyCountOf(reportId)).isZero();
            login(TARGET_EMAIL).andExpect(status().isOk());
        }

        @Test
        @DisplayName("인용하면 압수한 포인트가 돌아온다")
        void acceptRefundsForfeitedPoints() throws Exception {
            var target = loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            Long targetId = userIdOf(TARGET_EMAIL);
            givePoints(targetId, 500);
            Long reportId = acceptWith(pointForfeit(200));
            assertThat(balanceOf(targetId)).isEqualTo(300);

            Long appealId = submitAppealAndGetId(target.accessToken(), reportId);
            var admin = loginAsAdmin();
            decideAppeal(admin.accessToken(), appealId, true).andExpect(status().isOk());

            assertThat(balanceOf(targetId)).isEqualTo(500);
        }

        @Test
        @DisplayName("잔액이 모자라 덜 깎였으면 깎인 만큼만 돌아온다")
        void acceptRefundsOnlyWhatWasTaken() throws Exception {
            var target = loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            Long targetId = userIdOf(TARGET_EMAIL);
            givePoints(targetId, 50);
            Long reportId = acceptWith(pointForfeit(200));
            assertThat(balanceOf(targetId)).isZero();

            Long appealId = submitAppealAndGetId(target.accessToken(), reportId);
            var admin = loginAsAdmin();
            decideAppeal(admin.accessToken(), appealId, true).andExpect(status().isOk());

            assertThat(balanceOf(targetId)).isEqualTo(50);
        }

        @Test
        @DisplayName("압수와 환급이 포인트 이력에 사유와 잔액까지 남는다")
        void refundLeavesPointHistory() throws Exception {
            var target = loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            Long targetId = userIdOf(TARGET_EMAIL);
            givePoints(targetId, 500);
            Long reportId = acceptWith(pointForfeit(200));

            Long appealId = submitAppealAndGetId(target.accessToken(), reportId);
            var admin = loginAsAdmin();
            decideAppeal(admin.accessToken(), appealId, true).andExpect(status().isOk());

            assertThat(pointHistoriesOf(targetId))
                    .extracting("reason", "amount", "balance_after")
                    .containsExactly(tuple("PENALTY_FORFEIT", -200, 300), tuple("PENALTY_REFUND", 200, 500));
        }

        @Test
        @DisplayName("한 푼도 못 깎았으면 포인트 이력이 남지 않는다")
        void noPointHistoryWhenNothingWasTaken() throws Exception {
            var target = loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            Long targetId = userIdOf(TARGET_EMAIL);
            Long reportId = acceptWith(pointForfeit(200));

            Long appealId = submitAppealAndGetId(target.accessToken(), reportId);
            var admin = loginAsAdmin();
            decideAppeal(admin.accessToken(), appealId, true).andExpect(status().isOk());

            assertThat(pointHistoriesOf(targetId)).isEmpty();
        }

        @Test
        @DisplayName("기각하면 압수한 포인트도 그대로다")
        void rejectKeepsForfeitedPoints() throws Exception {
            var target = loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            Long targetId = userIdOf(TARGET_EMAIL);
            givePoints(targetId, 500);
            Long reportId = acceptWith(pointForfeit(200));

            Long appealId = submitAppealAndGetId(target.accessToken(), reportId);
            var admin = loginAsAdmin();
            decideAppeal(admin.accessToken(), appealId, false).andExpect(status().isOk());

            assertThat(balanceOf(targetId)).isEqualTo(300);
        }

        @Test
        @DisplayName("기각하면 정지가 그대로다")
        void rejectKeepsPenalty() throws Exception {
            var target = loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            Long reportId = acceptWith(suspension(3));
            Long appealId = submitAppealAndGetId(target.accessToken(), reportId);
            var admin = loginAsAdmin();

            decideAppeal(admin.accessToken(), appealId, false)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("REJECTED"));

            assertThat(activePenaltyCountOf(reportId)).isEqualTo(1);
            login(TARGET_EMAIL).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("이미 판정이 끝난 이의제기는 다시 판정할 수 없다")
        void rejectsSecondAppealDecision() throws Exception {
            var target = loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            Long reportId = acceptWith(suspension(3));
            Long appealId = submitAppealAndGetId(target.accessToken(), reportId);
            var admin = loginAsAdmin();
            decideAppeal(admin.accessToken(), appealId, false).andExpect(status().isOk());

            decideAppeal(admin.accessToken(), appealId, true)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("APPEAL_ALREADY_DECIDED"));
        }

        @Test
        @DisplayName("관리자 이의제기 목록은 신고 상세를 함께 싣는다")
        void appealListCarriesReportDetail() throws Exception {
            var target = loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            Long reportId = acceptWith(suspension(3));
            submitAppeal(target.accessToken(), reportId, "억울합니다").andExpect(status().isCreated());
            var admin = loginAsAdmin();

            getAppeals(admin.accessToken(), "?status=PENDING")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].content").value("억울합니다"))
                    .andExpect(jsonPath("$.content[0].appellantId").value(userIdOf(TARGET_EMAIL)))
                    .andExpect(jsonPath("$.content[0].report.id").value(reportId))
                    .andExpect(jsonPath("$.content[0].report.status").value("ACCEPTED"))
                    // 인용하면 풀릴 제재라 목록에서도 보여야 한다
                    .andExpect(
                            jsonPath("$.content[0].report.penalties.length()").value(1))
                    .andExpect(jsonPath("$.content[0].report.penalties[0].penaltyType")
                            .value("SUSPENSION"));
        }
    }

    @Nested
    @DisplayName("관리자 직권 제재")
    class DirectPenalty {

        @Test
        @DisplayName("신고 없이 제재하면 승인 상태의 신고가 함께 만들어진다")
        void createsAcceptedReportAlongsidePenalty() throws Exception {
            loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            Long targetId = userIdOf(TARGET_EMAIL);
            var admin = loginAsAdmin();

            penalizeDirectly(admin.accessToken(), directPenaltyBody(TARGET_EMAIL, suspension(3)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("ACCEPTED"))
                    .andExpect(jsonPath("$.reason").value("ETC"))
                    .andExpect(jsonPath("$.reporterId").value(userIdOf(ADMIN_EMAIL)))
                    .andExpect(jsonPath("$.penalties[0].penaltyType").value("SUSPENSION"));

            login(TARGET_EMAIL).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("직권 제재는 콘텐츠 조치를 하지 않는다")
        void directPenaltyKeepsProfile() throws Exception {
            var target = loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            var admin = loginAsAdmin();

            penalizeDirectly(admin.accessToken(), directPenaltyBody(TARGET_EMAIL, WARNING))
                    .andExpect(status().isCreated());

            getMyProfile(target.accessToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nickname").value(TARGET_NICKNAME));
        }

        @Test
        @DisplayName("같은 사용자에게 직권 제재를 두 번 걸 수 있다")
        void allowsRepeatedDirectPenalty() throws Exception {
            loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            Long targetId = userIdOf(TARGET_EMAIL);
            var admin = loginAsAdmin();

            penalizeDirectly(admin.accessToken(), directPenaltyBody(TARGET_EMAIL, WARNING))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.pastPenaltyCount").value(1));
            penalizeDirectly(admin.accessToken(), directPenaltyBody(TARGET_EMAIL, WARNING))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.pastPenaltyCount").value(2));
        }

        @Test
        @DisplayName("직권 제재에도 이의제기할 수 있다")
        void allowsAppealOnDirectPenalty() throws Exception {
            var target = loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            var admin = loginAsAdmin();
            String body = bodyOf(penalizeDirectly(admin.accessToken(), directPenaltyBody(TARGET_EMAIL, suspension(3)))
                    .andExpect(status().isCreated()));

            submitAppeal(target.accessToken(), Long.parseLong(fieldOf(body, "id")), "신고도 없이 제재를 받았습니다")
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("상황 설명 없이 직권 제재하면 400")
        void rejectsDirectPenaltyWithoutDetail() throws Exception {
            loginAs(TARGET_EMAIL, TARGET_NICKNAME);
            var admin = loginAsAdmin();

            penalizeDirectly(admin.accessToken(), """
                    {"email":"%s","detail":"","penalties":[%s]}
                    """.formatted(TARGET_EMAIL, WARNING))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("없는 사용자에게 직권 제재하면 404")
        void rejectsDirectPenaltyOnMissingUser() throws Exception {
            var admin = loginAsAdmin();

            penalizeDirectly(admin.accessToken(), directPenaltyBody("nobody@example.com", WARNING))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        }
    }
}
