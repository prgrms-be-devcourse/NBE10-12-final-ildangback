package com.gommit.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gommit.support.IntegrationTestSupport;
import com.gommit.support.RecordingMailSender;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

@DisplayName("이메일 인증 API")
class EmailVerificationApiIntegrationTest extends IntegrationTestSupport {

    private static final String EMAIL = "gommit@example.com";
    private static final String NICKNAME = "꼬밋러";

    @Autowired
    private RecordingMailSender mailSender;

    @Nested
    @DisplayName("가입 시 발송")
    class SendOnSignUp {

        @Test
        @DisplayName("가입하면 인증 링크가 발송되고 미인증 상태로 시작한다")
        void sendsLinkAndStartsUnverified() throws Exception {
            signUp().andExpect(status().isCreated());

            assertThat(mailSender.lastRecipient()).isEqualTo(EMAIL);
            assertThat(mailSender.lastToken()).isNotBlank();
            assertThat(emailVerifiedOf(EMAIL)).isFalse();
        }

        // 링크가 프론트로 가면 프론트 화면 없이는 인증이 끝나지 않는다
        @Test
        @DisplayName("인증 링크는 백엔드 엔드포인트를 가리킨다")
        void linksToBackendEndpoint() throws Exception {
            signUp();

            assertThat(mailSender.lastBody()).contains("http://localhost:8080/api/auth/verify-email?token=");
        }

        @Test
        @DisplayName("메일 발송이 실패해도 가입은 성공한다")
        void signUpSucceedsWhenMailFails() throws Exception {
            mailSender.clear();
            mailSender.failOnSend();

            signUpWithoutClearing().andExpect(status().isCreated());

            assertThat(emailVerifiedOf(EMAIL)).isFalse();
        }
    }

    @Nested
    @DisplayName("인증")
    class Verify {

        @Test
        @DisplayName("링크의 토큰으로 인증하면 성공 화면으로 보내고 email_verified 가 켜진다")
        void verifiesWithToken() throws Exception {
            signUp();

            verify(mailSender.lastToken())
                    .andExpect(status().isFound())
                    .andExpect(redirectedUrl("http://localhost:5173/verify-result?status=success"));

            assertThat(emailVerifiedOf(EMAIL)).isTrue();
        }

        @Test
        @DisplayName("같은 토큰을 두 번 쓰면 실패 화면으로 보낸다. 1회용이다")
        void rejectsUsedToken() throws Exception {
            signUp();
            String token = mailSender.lastToken();

            verify(token).andExpect(redirectedUrl("http://localhost:5173/verify-result?status=success"));

            verify(token).andExpect(redirectedUrl("http://localhost:5173/verify-result?status=invalid"));
        }

        @Test
        @DisplayName("만료된 토큰은 실패 화면으로 보낸다")
        void rejectsExpiredToken() throws Exception {
            signUp();
            String token = mailSender.lastToken();
            expireAllTokens();

            verify(token).andExpect(redirectedUrl("http://localhost:5173/verify-result?status=invalid"));

            assertThat(emailVerifiedOf(EMAIL)).isFalse();
        }

        @Test
        @DisplayName("내 정보 응답의 emailVerified 가 인증 전후로 바뀐다")
        void exposesVerifiedFlagInProfile() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);

            me(tokens.accessToken()).andExpect(jsonPath("$.emailVerified").value(false));

            verify(mailSender.lastToken());

            me(tokens.accessToken()).andExpect(jsonPath("$.emailVerified").value(true));
        }

        @Test
        @DisplayName("없는 토큰은 실패 화면으로 보낸다")
        void rejectsUnknownToken() throws Exception {
            verify("never-existed").andExpect(redirectedUrl("http://localhost:5173/verify-result?status=invalid"));
        }
    }

    @Nested
    @DisplayName("재발송")
    class Resend {

        @Test
        @DisplayName("미인증 사용자는 인증 메일을 다시 받는다")
        void resendsForUnverifiedUser() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            passMinInterval();
            mailSender.clear();

            resend(tokens.accessToken()).andExpect(status().isNoContent());

            assertThat(mailSender.lastToken()).isNotBlank();
        }

        @Test
        @DisplayName("최소 간격 안에 또 요청하면 429")
        void rejectsWithinCooldown() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);

            resend(tokens.accessToken())
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("EMAIL_RESEND_TOO_SOON"));
        }

        @Test
        @DisplayName("이미 인증했으면 409")
        void rejectsVerifiedUser() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            verify(mailSender.lastToken());
            passMinInterval();

            resend(tokens.accessToken())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_VERIFIED"));
        }

        @Test
        @DisplayName("미인증이면 401")
        void requiresAuthentication() throws Exception {
            mockMvc.perform(post("/api/auth/verify-email/resend")).andExpect(status().isUnauthorized());
        }
    }

    private ResultActions me(String accessToken) throws Exception {
        return mockMvc.perform(withToken(get("/api/users/me"), accessToken));
    }

    private ResultActions resend(String accessToken) throws Exception {
        return mockMvc.perform(withToken(post("/api/auth/verify-email/resend"), accessToken));
    }

    // 최소 간격을 실제로 기다리지 않고 발급 시각을 과거로 민다
    private void passMinInterval() {
        jdbcTemplate.update(
                "UPDATE email_tokens SET created_at = ?", LocalDateTime.now().minusMinutes(10));
    }

    private ResultActions signUp() throws Exception {
        mailSender.clear();
        return signUpWithoutClearing();
    }

    private ResultActions signUpWithoutClearing() throws Exception {
        return mockMvc.perform(jsonRequest(
                post("/api/auth/signup"), json("email", EMAIL, "password", DEFAULT_PASSWORD, "nickname", NICKNAME)));
    }

    // 메일 링크를 누른 것과 같다. 결과 화면으로 리다이렉트된다
    private ResultActions verify(String token) throws Exception {
        return mockMvc.perform(get("/api/auth/verify-email").param("token", token));
    }

    private boolean emailVerifiedOf(String email) {
        return Boolean.TRUE.equals(
                jdbcTemplate.queryForObject("SELECT email_verified FROM users WHERE email = ?", Boolean.class, email));
    }

    private void expireAllTokens() {
        jdbcTemplate.update(
                "UPDATE email_tokens SET expires_at = ?", LocalDateTime.now().minusDays(1));
    }
}
