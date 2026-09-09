package com.gommit.domain.profile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gommit.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("프로필 API")
class ProfileApiIntegrationTest extends IntegrationTestSupport {

    // ─── 프로필 조회 ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("프로필 조회 GET /api/users/me/profile")
    class GetMyProfile {

        @Test
        @DisplayName("미인증이면 401")
        void t1() throws Exception {
            mockMvc.perform(get("/api/users/me/profile"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("인증하면 200과 character 필드를 포함한 ProfileResponse를 반환한다")
        void t2() throws Exception {
            var tokens = loginAs();

            mockMvc.perform(withToken(get("/api/users/me/profile"), tokens.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.character").exists())
                    .andExpect(jsonPath("$.character.slots").exists())
                    .andExpect(jsonPath("$.character.checkInState").exists());
        }

        @Test
        @DisplayName("착용 아이템이 없으면 모든 슬롯이 null인 character가 반환된다")
        void t3() throws Exception {
            var tokens = loginAs();

            mockMvc.perform(withToken(get("/api/users/me/profile"), tokens.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.character.slots.HEAD").doesNotExist())
                    .andExpect(jsonPath("$.character.slots.TOP").doesNotExist())
                    .andExpect(jsonPath("$.character.slots.BOTTOM").doesNotExist())
                    .andExpect(jsonPath("$.character.slots.SHOES").doesNotExist());
        }

        @Test
        @DisplayName("오늘 체크인 이력이 없으면 checkInState가 NOT_DONE이다")
        void t4() throws Exception {
            var tokens = loginAs();

            mockMvc.perform(withToken(get("/api/users/me/profile"), tokens.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.character.checkInState").value("NOT_DONE"));
        }
    }
}
