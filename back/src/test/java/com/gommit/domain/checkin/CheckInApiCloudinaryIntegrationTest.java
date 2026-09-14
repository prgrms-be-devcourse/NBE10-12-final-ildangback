package com.gommit.domain.checkin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gommit.domain.media.entity.MediaRole;
import com.gommit.domain.media.service.StorageService;
import com.gommit.support.IntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

// CheckInApiIntegrationTest.VideoSubmission 과 같은 영상 인증 제출 e2e 를, provider=local 이 아니라
// 실제 Cloudinary 계정으로 태운다. media.storage.provider 를 cloudinary 로 오버라이드하므로
// Spring 이 별도 ApplicationContext 를 띄운다(캐시 키가 다름) — CloudinaryStorageService 가
// 진짜 컨트롤러->서비스->CheckInMediaStore 경로에 꽂혀 동작하는지까지 검증하는 것이 목적.
// root-folder 를 이 테스트 전용 하위 폴더로 격리해 실제 계정을 어지르지 않는다.
// CLOUDINARY_CLOUD_NAME(및 API_KEY/API_SECRET) 이 없으면(로컬 기본, CI 시크릿 미설정 등) 스킵.
// ffmpeg 도 PATH 에 있어야 한다(CheckInApiIntegrationTest 와 동일한 이유로 판정 메서드는 로컬에 따로 둔다 —
// AbstractFfmpegTest 는 IntegrationTestSupport 와 동시 상속이 안 된다).
@DisplayName("체크인 API 영상 제출 (Cloudinary)")
@EnabledIfEnvironmentVariable(named = "CLOUDINARY_CLOUD_NAME", matches = ".+")
@EnabledIf("com.gommit.domain.checkin.CheckInApiCloudinaryIntegrationTest#ffmpegAvailable")
@TestPropertySource(
        properties = {
            "media.storage.provider=cloudinary",
            "media.storage.cloudinary.root-folder=test-go-mmit/checkin-video-e2e"
        })
class CheckInApiCloudinaryIntegrationTest extends IntegrationTestSupport {

    private static final String EMAIL = "checkin-cloudinary@example.com";
    private static final String NICKNAME = "인증러";

    @Autowired
    private StorageService storageService;

    private Long createdCheckInId;

    // 이 테스트가 만든 Cloudinary 리소스(영상+포스터)를 실제로 지운다. best-effort —
    // 실패해도 다음 테스트를 막지 않는다(orphan 은 root-folder 격리로 무해하다).
    @AfterEach
    void cleanupCloudinaryMedia() {
        if (createdCheckInId == null) {
            return;
        }
        jdbcTemplate
                .queryForList("select media_key, poster_key from check_ins where id = ?", createdCheckInId)
                .forEach(row -> {
                    deleteQuietly((String) row.get("media_key"));
                    deleteQuietly((String) row.get("poster_key"));
                });
    }

    private void deleteQuietly(String storageKey) {
        if (storageKey == null) {
            return;
        }
        try {
            storageService.delete(storageKey, MediaRole.CHECKIN);
        } catch (RuntimeException ignored) {
            // best-effort 정리 — 실패해도 테스트 결과에 영향 없음
        }
    }

    @Test
    @DisplayName("영상으로 제출하면 실제 Cloudinary 에 mp4/포스터가 올라가고, 서버가 서명 URL 로 받아와 서빙한다")
    void submitVideoAndServeMediaAndPosterViaCloudinary() throws Exception {
        var tokens = loginAs(EMAIL, NICKNAME);
        long challengeId = setUpChallenge(EMAIL, 3);
        jdbcTemplate.update("update challenges set allow_video = true where id = ?", challengeId);

        // 회차 길이(2초)보다 짧은 원본 — 패딩 경로까지 실제 ffmpeg 로 검증.
        var media = new MockMultipartFile("media", "clip.mp4", "video/mp4", syntheticVideoBytes(0.5));
        var builder = multipart("/api/challenges/{challengeId}/check-ins", challengeId)
                .file(media)
                .param("checkInType", "VIDEO")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken());

        ResultActions result = mockMvc.perform(builder);
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.checkIn.mediaType").value("VIDEO"))
                .andExpect(jsonPath("$.checkIn.posterUrl")
                        .value(Matchers.matchesPattern("/api/check-ins/\\d+/media/poster")));

        String body = result.andReturn().getResponse().getContentAsString();
        createdCheckInId = ((Number) JsonPath.read(body, "$.checkIn.id")).longValue();
        String mediaUrl = JsonPath.read(body, "$.checkIn.mediaUrl");
        String posterUrl = JsonPath.read(body, "$.checkIn.posterUrl");

        // storageKey 가 이 테스트 전용 root-folder 아래에 있어야 한다 — local 로 조용히 폴백하지 않았다는 증거.
        String mediaKey = jdbcTemplate.queryForObject(
                "select media_key from check_ins where id = ?", String.class, createdCheckInId);
        assertThat(mediaKey)
                .startsWith("test-go-mmit/checkin-video-e2e/check-ins/")
                .endsWith(".mp4");

        mockMvc.perform(withToken(get(mediaUrl), tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("video/mp4"));

        mockMvc.perform(withToken(get(posterUrl), tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/jpeg"));
    }

    private byte[] syntheticVideoBytes(double durationSeconds) throws Exception {
        Path tmp = Files.createTempFile("checkin-api-cloudinary-video-", ".mp4");
        try {
            new ProcessBuilder(
                            "ffmpeg",
                            "-y",
                            "-f",
                            "lavfi",
                            "-i",
                            "testsrc=size=320x240:rate=30:duration=%s".formatted(durationSeconds),
                            tmp.toString())
                    .redirectErrorStream(true)
                    .start()
                    .waitFor();
            return Files.readAllBytes(tmp);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    static boolean ffmpegAvailable() {
        try {
            return new ProcessBuilder("ffmpeg", "-version")
                            .redirectErrorStream(true)
                            .start()
                            .waitFor()
                    == 0;
        } catch (Exception e) {
            return false;
        }
    }

    // ===== 시딩 (CheckInApiIntegrationTest 와 동일 — provider 오버라이드로 별도 컨텍스트라 공유 못 한다) =====

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

    private long seedChallenge(long groupId, int dailyCheckInCount) {
        jdbcTemplate.update(
                "insert into challenges"
                        + " (group_id, seq_no, start_date, end_date, status, frequency_type, frequency_value,"
                        + " days_of_week, daily_check_in_count, required_day_count, group_current_streak,"
                        + " group_best_streak, allow_photo, created_at, updated_at)"
                        + " values (?, 1, ?, ?, 'ACTIVE', 'DAILY', null, null, ?, 30, 0, 0, true, now(6), now(6))",
                groupId,
                LocalDate.now().minusDays(10),
                LocalDate.now().plusDays(30),
                dailyCheckInCount);
        return jdbcTemplate.queryForObject("select id from challenges order by id desc limit 1", Long.class);
    }

    private void seedMember(long challengeId, long userId) {
        jdbcTemplate.update(
                "insert into challenge_members"
                        + " (challenge_id, user_id, role, status, current_streak, best_streak, left_at,"
                        + " extension_choice, created_at, updated_at)"
                        + " values (?, ?, 'MEMBER', 'ACTIVE', 0, 0, null, 'PENDING', now(6), now(6))",
                challengeId,
                userId);
    }

    private long setUpChallenge(String memberEmail, int dailyCheckInCount) {
        long userId = userIdOf(memberEmail);
        long groupId = seedGroup(userId);
        long challengeId = seedChallenge(groupId, dailyCheckInCount);
        seedMember(challengeId, userId);
        return challengeId;
    }
}
