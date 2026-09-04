package com.gommit.domain.checkin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gommit.domain.checkin.dto.request.SubmitCheckInForm;
import com.gommit.domain.checkin.entity.CheckInType;
import com.gommit.domain.checkin.service.CheckInService;
import com.gommit.global.exception.BusinessException;
import com.gommit.support.IntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

@DisplayName("인증 API")
class CheckInApiIntegrationTest extends IntegrationTestSupport {

    private static final String EMAIL = "checkin@example.com";
    private static final String NICKNAME = "인증러";
    // 1x1 PNG (매직바이트 포함) — 저장소 검증을 통과한다.
    private static final byte[] PNG_1X1 = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D
    };

    private static final Path MEDIA_DIR = Path.of("build/test-media");

    @Autowired
    private CheckInService checkInService;

    // 통합 테스트가 실제 파일을 쓰므로, orphan 여부를 검증할 수 있도록 매 테스트 전에 미디어 디렉토리를 비운다.
    @BeforeEach
    void wipeMediaDir() throws IOException {
        if (!Files.exists(MEDIA_DIR)) {
            return;
        }
        try (var walk = Files.walk(MEDIA_DIR)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                    // best-effort
                }
            });
        }
    }

    // ===== 시딩 (challenge 도메인에 생성 로직이 없어 직접 넣는다) =====

    private long userIdOf(String email) {
        return jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, email);
    }

    private long seedGroup(long ownerId) {
        jdbcTemplate.update(
                "insert into challenge_groups"
                        + " (name, description, category, map_type, visibility, max_members, owner_id, status, created_at, updated_at)"
                        + " values ('테스트그룹', null, 'DEV', 'STUDY_ROOM', 'PUBLIC', 10, ?, 'ACTIVE', now(6), now(6))",
                ownerId);
        return jdbcTemplate.queryForObject("select id from challenge_groups order by id desc limit 1", Long.class);
    }

    private long seedChallenge(long groupId, int dailyCheckInCount, String challengeStatus) {
        jdbcTemplate.update(
                "insert into challenges"
                        + " (group_id, seq_no, start_date, end_date, status, frequency_type, frequency_value,"
                        + " days_of_week, daily_check_in_count, required_day_count, group_current_streak,"
                        + " group_best_streak, allow_photo, created_at, updated_at)"
                        + " values (?, 1, ?, ?, ?, 'DAILY', null, null, ?, 30, 0, 0, true, now(6), now(6))",
                groupId,
                LocalDate.now().minusDays(10),
                LocalDate.now().plusDays(30),
                challengeStatus,
                dailyCheckInCount);
        return jdbcTemplate.queryForObject("select id from challenges order by id desc limit 1", Long.class);
    }

    private void seedMember(long challengeId, long userId, String memberStatus, LocalDate leftOn) {
        jdbcTemplate.update(
                "insert into challenge_members"
                        + " (challenge_id, user_id, role, status, current_streak, best_streak, left_at,"
                        + " extension_choice, created_at, updated_at)"
                        + " values (?, ?, 'MEMBER', ?, 0, 0, ?, 'PENDING', now(6), now(6))",
                challengeId,
                userId,
                memberStatus,
                leftOn == null ? null : leftOn.atStartOfDay());
    }

    private long setUpChallenge(String memberEmail, int dailyCheckInCount) {
        long userId = userIdOf(memberEmail);
        long groupId = seedGroup(userId);
        long challengeId = seedChallenge(groupId, dailyCheckInCount, "ACTIVE");
        seedMember(challengeId, userId, "ACTIVE", null);
        return challengeId;
    }

    // ===== 요청 헬퍼 =====

    private MockMultipartHttpServletRequestBuilder submitRequest(long challengeId, String token, String memo) {
        var media = new MockMultipartFile("media", "shot.png", "image/png", PNG_1X1);
        var builder = multipart("/api/challenges/{challengeId}/check-ins", challengeId)
                .file(media)
                .param("checkInType", "PHOTO")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (memo != null) {
            builder.param("memo", memo);
        }
        return builder;
    }

    private ResultActions submit(long challengeId, String token) throws Exception {
        return mockMvc.perform(submitRequest(challengeId, token, null));
    }

    private String mediaUrlOf(ResultActions submitResult) throws Exception {
        String body = submitResult.andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.checkIn.mediaUrl");
    }

    // ===== 테스트 =====

    @Test
    @DisplayName("토큰 없이 호출하면 401")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/challenges/1/check-ins/today"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Nested
    @DisplayName("인증 제출")
    class Submit {

        @Test
        @DisplayName("제출하면 201 과 인증 결과를 주고 포인트가 적립된다 — roundNo 1")
        void submitReturns201() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            long userId = userIdOf(EMAIL);
            long challengeId = setUpChallenge(EMAIL, 3);

            submit(challengeId, tokens.accessToken())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.checkIn.roundNo").value(1))
                    .andExpect(jsonPath("$.checkIn.nickname").value(NICKNAME))
                    .andExpect(jsonPath("$.checkIn.mediaType").value("IMAGE"))
                    .andExpect(jsonPath("$.currentCount").value(1))
                    .andExpect(jsonPath("$.targetCount").value(3))
                    .andExpect(jsonPath("$.dailyCompleted").value(false))
                    .andExpect(jsonPath("$.earnedUserPoints").value(10));

            assertThat(jdbcTemplate.queryForObject(
                            "select balance from user_points where user_id = ?", Integer.class, userId))
                    .isEqualTo(10);
            var history = jdbcTemplate.queryForMap(
                    "select amount, reason, source_name from user_point_histories where user_id = ?", userId);
            assertThat(history)
                    .containsEntry("amount", 10)
                    .containsEntry("reason", "CHECK_IN")
                    .containsEntry("source_name", "인증");
        }

        @Test
        @DisplayName("하루 목표 회차를 초과하면 409 DAILY_LIMIT_EXCEEDED")
        void rejectsOverDailyLimit() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            long challengeId = setUpChallenge(EMAIL, 1);

            submit(challengeId, tokens.accessToken()).andExpect(status().isCreated());
            submit(challengeId, tokens.accessToken())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DAILY_LIMIT_EXCEEDED"));
        }

        @Test
        @DisplayName("메모가 100자를 넘으면 400 MEMO_TOO_LONG")
        void rejectsLongMemo() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            long challengeId = setUpChallenge(EMAIL, 3);

            mockMvc.perform(submitRequest(challengeId, tokens.accessToken(), "가".repeat(101)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MEMO_TOO_LONG"));
        }

        @Test
        @DisplayName("지원하지 않는 파일 형식이면 415 UNSUPPORTED_MEDIA_TYPE")
        void rejectsUnsupportedMedia() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            long challengeId = setUpChallenge(EMAIL, 3);

            var builder = multipart("/api/challenges/{challengeId}/check-ins", challengeId)
                    .file(new MockMultipartFile("media", "a.txt", "text/plain", new byte[] {1, 2, 3}))
                    .param("checkInType", "PHOTO")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken());
            mockMvc.perform(builder)
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        }

        @Test
        @DisplayName("multipart 최대 크기를 넘으면 413 FILE_TOO_LARGE")
        void rejectsTooLargeUpload() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            long challengeId = setUpChallenge(EMAIL, 3);

            var big = new MockMultipartFile("media", "big.png", "image/png", new byte[16 * 1024 * 1024]);
            var builder = multipart("/api/challenges/{challengeId}/check-ins", challengeId)
                    .file(big)
                    .param("checkInType", "PHOTO")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken());
            mockMvc.perform(builder)
                    .andExpect(status().isPayloadTooLarge())
                    .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
        }

        @Test
        @DisplayName("참여자가 아니면 403 NOT_CHALLENGE_MEMBER")
        void rejectsNonMember() throws Exception {
            loginAs(EMAIL, NICKNAME);
            long challengeId = setUpChallenge(EMAIL, 3);
            var outsider = loginAs("outsider@example.com", "외부인");

            submit(challengeId, outsider.accessToken())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NOT_CHALLENGE_MEMBER"));
        }

        @Test
        @DisplayName("없는 챌린지면 404 CHALLENGE_NOT_FOUND")
        void rejectsUnknownChallenge() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);

            submit(999_999L, tokens.accessToken())
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CHALLENGE_NOT_FOUND"));
        }

        @Test
        @DisplayName("순차 제출은 roundNo 가 1,2,3 으로 증가하고 목표 초과 시 409")
        void sequentialSubmitsIncrementRoundNo() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            long challengeId = setUpChallenge(EMAIL, 3);

            submit(challengeId, tokens.accessToken())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.checkIn.roundNo").value(1));
            submit(challengeId, tokens.accessToken())
                    .andExpect(jsonPath("$.checkIn.roundNo").value(2));
            submit(challengeId, tokens.accessToken())
                    .andExpect(jsonPath("$.checkIn.roundNo").value(3))
                    .andExpect(jsonPath("$.dailyCompleted").value(true));
            submit(challengeId, tokens.accessToken())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DAILY_LIMIT_EXCEEDED"));

            List<Integer> rounds = jdbcTemplate.queryForList(
                    "select round_no from check_ins where challenge_id = ? order by round_no",
                    Integer.class,
                    challengeId);
            assertThat(rounds).containsExactly(1, 2, 3);
        }

        // MockMvc 는 스레드 안전이 아니므로 서비스를 직접 호출한다 (signup 동시성 테스트와 동일 패턴).
        @Test
        @DisplayName("동시 제출은 uk_check_ins 로 걸러지고 저장 수 = check_ins 행 수 (orphan 없음)")
        void concurrentSubmitsRespectDailyLimit() throws Exception {
            loginAs(EMAIL, NICKNAME);
            long userId = userIdOf(EMAIL);
            long challengeId = setUpChallenge(EMAIL, 2);

            int parallel = 8;
            CyclicBarrier barrier = new CyclicBarrier(parallel);
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < parallel; i++) {
                tasks.add(() -> {
                    barrier.await();
                    try {
                        checkInService.submit(
                                userId,
                                challengeId,
                                new SubmitCheckInForm(CheckInType.PHOTO, null),
                                new MockMultipartFile("media", "shot.png", "image/png", PNG_1X1));
                        return true;
                    } catch (BusinessException e) {
                        return false;
                    }
                });
            }

            ExecutorService pool = Executors.newFixedThreadPool(parallel);
            long succeeded;
            try {
                List<Future<Boolean>> results = pool.invokeAll(tasks);
                succeeded = results.stream()
                        .filter(r -> {
                            try {
                                return r.get();
                            } catch (Exception e) {
                                throw new IllegalStateException(e);
                            }
                        })
                        .count();
            } finally {
                pool.shutdownNow();
            }

            int rows = jdbcTemplate.queryForObject(
                    "select count(*) from check_ins where challenge_id = ? and user_id = ?",
                    Integer.class,
                    challengeId,
                    userId);
            long mediaFiles = countMediaFiles();

            // 락은 없다. 동시 버스트는 uk_check_ins 로 걸러져 목표 회차(2) 를 넘지 않고,
            // 성공 수 == 저장된 행 수 == 디스크 파일 수 (실패한 요청은 파일을 남기지 않는다).
            assertThat(succeeded).isBetween(1L, 2L);
            assertThat(rows).isEqualTo((int) succeeded);
            assertThat(mediaFiles).isEqualTo(succeeded);
        }
    }

    private long countMediaFiles() throws IOException {
        if (!Files.exists(MEDIA_DIR)) {
            return 0;
        }
        try (var walk = Files.walk(MEDIA_DIR)) {
            return walk.filter(Files::isRegularFile).count();
        }
    }

    @Nested
    @DisplayName("조회")
    class Query {

        @Test
        @DisplayName("오늘 상태 — 제출 전후로 currentCount 가 바뀐다")
        void todayStatus() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            long challengeId = setUpChallenge(EMAIL, 2);

            mockMvc.perform(withToken(get("/api/challenges/{id}/check-ins/today", challengeId), tokens.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.currentCount").value(0))
                    .andExpect(jsonPath("$.targetCount").value(2))
                    .andExpect(jsonPath("$.isCheckInDay").value(true))
                    .andExpect(jsonPath("$.completed").value(false))
                    .andExpect(jsonPath("$.allowedTypes[0]").value("PHOTO"));

            submit(challengeId, tokens.accessToken());

            mockMvc.perform(withToken(get("/api/challenges/{id}/check-ins/today", challengeId), tokens.accessToken()))
                    .andExpect(jsonPath("$.currentCount").value(1));
        }

        @Test
        @DisplayName("갤러리 — 커서로 다음 페이지를 준다")
        void galleryPaginates() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            long challengeId = setUpChallenge(EMAIL, 5);

            submit(challengeId, tokens.accessToken());
            submit(challengeId, tokens.accessToken());
            submit(challengeId, tokens.accessToken());

            List<Long> ids = jdbcTemplate.queryForList("select id from check_ins order by id asc", Long.class);
            long expectedNextCursor = ids.get(1);

            mockMvc.perform(withToken(
                            get("/api/challenges/{id}/check-ins", challengeId).param("size", "2"),
                            tokens.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].id").value(ids.get(2)))
                    .andExpect(jsonPath("$.meta.hasNext").value(true))
                    .andExpect(jsonPath("$.meta.nextCursor").value(expectedNextCursor));

            mockMvc.perform(withToken(
                            get("/api/challenges/{id}/check-ins", challengeId)
                                    .param("size", "2")
                                    .param("cursor", String.valueOf(expectedNextCursor)),
                            tokens.accessToken()))
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(ids.get(0)))
                    .andExpect(jsonPath("$.meta.hasNext").value(false))
                    .andExpect(jsonPath("$.meta.nextCursor").value(Matchers.nullValue()));
        }

        @Test
        @DisplayName("갤러리 — userId 필터")
        void galleryFiltersByUser() throws Exception {
            var owner = loginAs(EMAIL, NICKNAME);
            long challengeId = setUpChallenge(EMAIL, 3);
            var other = loginAs("other@example.com", "다른사람");
            long otherId = userIdOf("other@example.com");
            seedMember(challengeId, otherId, "ACTIVE", null);

            submit(challengeId, owner.accessToken());
            submit(challengeId, other.accessToken());

            mockMvc.perform(withToken(
                            get("/api/challenges/{id}/check-ins", challengeId).param("userId", String.valueOf(otherId)),
                            owner.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].userId").value(otherId));
        }

        @Test
        @DisplayName("단건 조회 — 정상 / 없는 id 404 / 다른 챌린지 403")
        void getCheckIn() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            long challengeId = setUpChallenge(EMAIL, 3);
            long otherChallengeId = seedChallenge(seedGroup(userIdOf(EMAIL)), 3, "ACTIVE");
            submit(challengeId, tokens.accessToken());

            long checkInId =
                    jdbcTemplate.queryForObject("select id from check_ins order by id desc limit 1", Long.class);

            mockMvc.perform(withToken(
                            get("/api/challenges/{cid}/check-ins/{id}", challengeId, checkInId), tokens.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(checkInId));

            mockMvc.perform(withToken(
                            get("/api/challenges/{cid}/check-ins/{id}", challengeId, 999_999L), tokens.accessToken()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CHECK_IN_NOT_FOUND"));

            mockMvc.perform(withToken(
                            get("/api/challenges/{cid}/check-ins/{id}", otherChallengeId, checkInId),
                            tokens.accessToken()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NOT_CHALLENGE_MEMBER"));
        }

        @Test
        @DisplayName("최근 로그 — 한 줄 텍스트를 준다")
        void recent() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            long challengeId = setUpChallenge(EMAIL, 3);
            submit(challengeId, tokens.accessToken());

            mockMvc.perform(withToken(get("/api/challenges/{id}/check-ins/recent", challengeId), tokens.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.items[0].nickname").value(NICKNAME))
                    .andExpect(jsonPath("$.items[0].earnedUserPoints").value(Matchers.nullValue()));
        }
    }

    @Nested
    @DisplayName("이탈 멤버 조회")
    class LeftMember {

        @Test
        @DisplayName("이탈 후 날짜의 단건은 403, 참여 기간 내 기록은 조회된다")
        void leftMemberSeesOnlyTenure() throws Exception {
            var owner = loginAs(EMAIL, NICKNAME);
            long challengeId = setUpChallenge(EMAIL, 3);
            submit(challengeId, owner.accessToken());
            long checkInId =
                    jdbcTemplate.queryForObject("select id from check_ins order by id desc limit 1", Long.class);

            // 이 인증은 어제 것으로 만들고, 뷰어는 오늘 이탈한 것으로 만든다.
            jdbcTemplate.update(
                    "update check_ins set business_date = ? where id = ?",
                    LocalDate.now().minusDays(1),
                    checkInId);
            var viewer = loginAs("viewer@example.com", "이탈자");
            long viewerId = userIdOf("viewer@example.com");
            seedMember(challengeId, viewerId, "LEFT", LocalDate.now());

            mockMvc.perform(withToken(
                            get("/api/challenges/{cid}/check-ins/{id}", challengeId, checkInId), viewer.accessToken()))
                    .andExpect(status().isOk());

            // 이탈일 이후 날짜의 기록으로 바꾸면 403
            jdbcTemplate.update(
                    "update check_ins set business_date = ? where id = ?",
                    LocalDate.now().plusDays(1),
                    checkInId);
            mockMvc.perform(withToken(
                            get("/api/challenges/{cid}/check-ins/{id}", challengeId, checkInId), viewer.accessToken()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NOT_CHALLENGE_MEMBER"));
        }
    }

    @Nested
    @DisplayName("내 인증 모아보기")
    class MyCheckIns {

        @Test
        @DisplayName("challengeId 로 필터하고 challengeId 필드와 totalCount 를 준다")
        void listsMine() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            long challengeId = setUpChallenge(EMAIL, 3);
            submit(challengeId, tokens.accessToken());
            submit(challengeId, tokens.accessToken());

            mockMvc.perform(withToken(
                            get("/api/users/me/check-ins").param("challengeId", String.valueOf(challengeId)),
                            tokens.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].challengeId").value(challengeId))
                    .andExpect(jsonPath("$.meta.totalCount").value(2));
        }

        @Test
        @DisplayName("month 형식이 틀리면 400")
        void rejectsBadMonth() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);

            mockMvc.perform(withToken(get("/api/users/me/check-ins").param("month", "2026/09"), tokens.accessToken()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("없는 challengeId 를 지정하면 404")
        void rejectsUnknownChallenge() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);

            mockMvc.perform(withToken(
                            get("/api/users/me/check-ins").param("challengeId", "999999"), tokens.accessToken()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CHALLENGE_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("인증 미디어 조회")
    class MediaServing {

        @Test
        @DisplayName("참여자는 미디어 URL 로 파일을 받는다")
        void memberDownloadsMedia() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            long challengeId = setUpChallenge(EMAIL, 3);

            String mediaUrl = mediaUrlOf(submit(challengeId, tokens.accessToken()));

            assertThat(mediaUrl).matches("/api/check-ins/\\d+/media");
            mockMvc.perform(withToken(get(mediaUrl), tokens.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("image/png"));
        }

        @Test
        @DisplayName("비참여자가 미디어 URL 로 접근하면 403 NOT_CHALLENGE_MEMBER")
        void nonMemberRejected() throws Exception {
            var owner = loginAs(EMAIL, NICKNAME);
            long challengeId = setUpChallenge(EMAIL, 3);
            var outsider = loginAs("outsider@example.com", "외부인");

            String mediaUrl = mediaUrlOf(submit(challengeId, owner.accessToken()));

            mockMvc.perform(withToken(get(mediaUrl), outsider.accessToken()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NOT_CHALLENGE_MEMBER"));
        }

        @Test
        @DisplayName("없는 인증이면 404 CHECK_IN_NOT_FOUND")
        void unknownCheckInRejected() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);

            mockMvc.perform(withToken(get("/api/check-ins/999999/media"), tokens.accessToken()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CHECK_IN_NOT_FOUND"));
        }
    }
}
