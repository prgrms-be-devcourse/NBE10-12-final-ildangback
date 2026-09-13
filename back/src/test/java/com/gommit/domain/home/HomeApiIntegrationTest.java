package com.gommit.domain.home;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gommit.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("홈 API")
class HomeApiIntegrationTest extends IntegrationTestSupport {

    // ─── 홈 화면 조회 ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("홈 화면 조회 GET /api/home")
    class GetHome {

        @Test
        @DisplayName("미인증이면 401")
        void t1() throws Exception {
            mockMvc.perform(get("/api/home"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("인증하면 200과 HomeResponse 구조를 돌려준다")
        void t2() throws Exception {
            var tokens = loginAs();

            mockMvc.perform(withToken(get("/api/home"), tokens.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nickname").exists())
                    .andExpect(jsonPath("$.pointBalance").isNumber())
                    .andExpect(jsonPath("$.character").exists())
                    .andExpect(jsonPath("$.summary").exists())
                    .andExpect(jsonPath("$.todayChallenges").isArray())
                    .andExpect(jsonPath("$.todayTotalCount").isNumber())
                    .andExpect(jsonPath("$.todayCompletedCount").isNumber())
                    .andExpect(jsonPath("$.businessDate").exists());
        }

        @Test
        @DisplayName("신규 가입 유저는 닉네임과 포인트 0이 반환된다")
        void t3() throws Exception {
            var tokens = loginAs("home@example.com", "홈테스터");

            mockMvc.perform(withToken(get("/api/home"), tokens.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nickname").value("홈테스터"))
                    .andExpect(jsonPath("$.pointBalance").value(0))
                    .andExpect(jsonPath("$.todayTotalCount").value(0))
                    .andExpect(jsonPath("$.todayCompletedCount").value(0));
        }
    }

    // ─── 잔디 조회 ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("꼬밋 잔디 조회 GET /api/users/me/grass")
    class GetGrass {

        @Test
        @DisplayName("미인증이면 401")
        void t4() throws Exception {
            mockMvc.perform(get("/api/users/me/grass")
                            .param("from", "2026-01-01")
                            .param("to", "2026-01-31"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("유효한 날짜 범위이면 200과 날짜별 잔디 배열을 반환한다")
        void t5() throws Exception {
            var tokens = loginAs();

            mockMvc.perform(withToken(
                            get("/api/users/me/grass")
                                    .param("from", "2025-06-01")
                                    .param("to", "2025-06-03"),
                            tokens.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(3))
                    .andExpect(jsonPath("$.content[0].date").value("2025-06-01"))
                    .andExpect(jsonPath("$.content[0].checkInCount").isNumber())
                    .andExpect(jsonPath("$.content[0].level").isNumber());
        }

        @Test
        @DisplayName("from이 to보다 늦으면 400")
        void t6() throws Exception {
            var tokens = loginAs();

            mockMvc.perform(withToken(
                            get("/api/users/me/grass")
                                    .param("from", "2025-06-10")
                                    .param("to", "2025-06-01"),
                            tokens.accessToken()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("범위가 366일을 초과하면 400")
        void t7() throws Exception {
            var tokens = loginAs();

            mockMvc.perform(withToken(
                            get("/api/users/me/grass")
                                    .param("from", "2024-01-01")
                                    .param("to", "2025-01-03"),
                            tokens.accessToken()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("from 파라미터가 없으면 400")
        void t8() throws Exception {
            var tokens = loginAs();

            mockMvc.perform(withToken(get("/api/users/me/grass").param("to", "2025-06-30"), tokens.accessToken()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("to 파라미터가 없으면 400")
        void t9() throws Exception {
            var tokens = loginAs();

            mockMvc.perform(withToken(get("/api/users/me/grass").param("from", "2025-06-01"), tokens.accessToken()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("from == to이면 하루치 결과 1개가 반환된다")
        void t10() throws Exception {
            var tokens = loginAs();

            mockMvc.perform(withToken(
                            get("/api/users/me/grass")
                                    .param("from", "2025-06-15")
                                    .param("to", "2025-06-15"),
                            tokens.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1));
        }
    }

    // ─── 최근 활동 조회 ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("최근 활동 조회 GET /api/users/me/activities")
    class GetActivities {

        @Test
        @DisplayName("미인증이면 401")
        void t11() throws Exception {
            mockMvc.perform(get("/api/users/me/activities"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("인증하면 200과 SliceResponse 구조를 돌려준다")
        void t12() throws Exception {
            var tokens = loginAs();

            mockMvc.perform(withToken(get("/api/users/me/activities"), tokens.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("포인트 이력이 없으면 빈 content 배열이 반환된다")
        void t13() throws Exception {
            var tokens = loginAs();

            mockMvc.perform(withToken(get("/api/users/me/activities"), tokens.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(0));
        }
    }
}
