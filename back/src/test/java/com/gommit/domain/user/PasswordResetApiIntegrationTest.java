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

@DisplayName("비밀번호 재설정 API")
class PasswordResetApiIntegrationTest extends IntegrationTestSupport {

    private static final String EMAIL = "gommit@example.com";
    private static final String NICKNAME = "꼬밋러";
    private static final String NEW_PASSWORD = "N3wP@ssword!";

    @Autowired
    private RecordingMailSender mailSender;

    @Nested
    @DisplayName("메일 요청")
    class Request {

        @Test
        @DisplayName("인증을 마친 주소로 요청하면 프론트 재설정 화면을 가리키는 링크가 발송된다")
        void sendsLinkToFrontend() throws Exception {
            signUpAndVerify();
            mailSender.clear();

            request(EMAIL).andExpect(status().isNoContent());

            assertThat(mailSender.lastRecipient()).isEqualTo(EMAIL);
            assertThat(mailSender.lastSubject()).isEqualTo("[꼬밋] 비밀번호 재설정 안내");
            assertThat(mailSender.lastBody()).contains("http://localhost:5173/reset-password?token=");
            assertThat(mailSender.lastToken()).isNotBlank();
        }

        // 가입 여부를 응답으로 알려주면 계정 열거가 된다
        @Test
        @DisplayName("없는 주소도 204 를 주고 메일은 보내지 않는다")
        void staysSilentForUnknownEmail() throws Exception {
            mailSender.clear();

            request("nobody@example.com").andExpect(status().isNoContent());

            assertThat(mailSender.lastToken()).isNull();
        }

        // 막는 대신 인증 메일로 길을 터준다
        @Test
        @DisplayName("미인증 주소로 요청하면 재설정 메일 대신 인증 메일이 간다")
        void sendsVerificationMailWhenUnverified() throws Exception {
            signUp();
            ageTokens();
            mailSender.clear();

            request(EMAIL).andExpect(status().isNoContent());

            assertThat(mailSender.lastSubject()).isEqualTo("[꼬밋] 비밀번호 재설정 전에 이메일 인증이 필요합니다");
            assertThat(mailSender.lastBody())
                    .contains("/api/auth/verify-email?token=")
                    .contains("비밀번호 재설정을 요청하셨습니다");
        }

        // 인증을 끝내면 그때부터 재설정이 열린다. 막다른 길이 없다
        @Test
        @DisplayName("인증을 끝내고 다시 요청하면 재설정 메일이 간다")
        void opensAfterVerification() throws Exception {
            signUp();
            ageTokens();
            mailSender.clear();
            request(EMAIL);
            verify(mailSender.lastToken());
            mailSender.clear();

            request(EMAIL).andExpect(status().isNoContent());

            assertThat(mailSender.lastBody()).contains("http://localhost:5173/reset-password?token=");
        }

        // 따로 세지 않으면 인증 직후 60초 동안 재설정을 못 한다
        @Test
        @DisplayName("인증 직후라도 재설정 요청은 최소 간격에 걸리지 않는다")
        void countsSeparatelyFromVerificationMail() throws Exception {
            signUpAndVerify();
            mailSender.clear();

            request(EMAIL).andExpect(status().isNoContent());

            assertThat(mailSender.lastToken()).isNotBlank();
        }

        @Test
        @DisplayName("최소 간격 안에 또 요청하면 204 지만 메일은 나가지 않는다")
        void skipsWithinCooldown() throws Exception {
            signUpAndVerify();
            request(EMAIL);
            mailSender.clear();

            request(EMAIL).andExpect(status().isNoContent());

            assertThat(mailSender.lastToken()).isNull();
        }

        // 인증 메일과 같은 방침이다. SMTP 가 죽었다고 요청이 실패하면 안 된다
        @Test
        @DisplayName("메일 발송이 실패해도 요청은 204")
        void succeedsWhenMailFails() throws Exception {
            signUpAndVerify();
            mailSender.clear();
            mailSender.failOnSend();

            request(EMAIL).andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("이메일 형식이 아니면 400")
        void rejectsMalformedEmail() throws Exception {
            request("not-an-email").andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("재설정")
    class Confirm {

        @Test
        @DisplayName("새 비밀번호로 바뀌고 기존 비밀번호로는 로그인되지 않는다")
        void changesPassword() throws Exception {
            String token = issuedToken();

            confirm(token, NEW_PASSWORD).andExpect(status().isNoContent());

            login(NEW_PASSWORD).andExpect(status().isOk());
            login(DEFAULT_PASSWORD)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }

        // 비밀번호 변경과 같은 취급이다 (3-4)
        @Test
        @DisplayName("재설정하면 기존 RT 가 전부 폐기된다")
        void revokesAllRefreshTokens() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            verify(mailSender.lastToken());
            String token = requestAndTakeToken();

            confirm(token, NEW_PASSWORD).andExpect(status().isNoContent());

            mockMvc.perform(jsonRequest(post("/api/auth/refresh"), json("refreshToken", tokens.refreshToken())))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
        }

        // 재설정 토큰은 인증을 마친 계정에만 발급된다 (9-3)
        @Test
        @DisplayName("미인증 사용자에게는 재설정 토큰 자체가 발급되지 않는다")
        void issuesNoTokenForUnverifiedUser() throws Exception {
            signUp();
            mailSender.clear();

            request(EMAIL).andExpect(status().isNoContent());

            assertThat(resetTokenCount()).isZero();
        }

        @Test
        @DisplayName("같은 토큰을 두 번 쓰면 400. 1회용이다")
        void rejectsUsedToken() throws Exception {
            String token = issuedToken();
            confirm(token, NEW_PASSWORD).andExpect(status().isNoContent());

            confirm(token, "An0therP@ss!")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("EMAIL_TOKEN_INVALID"));
        }

        @Test
        @DisplayName("만료된 토큰은 400")
        void rejectsExpiredToken() throws Exception {
            String token = issuedToken();
            expireAllTokens();

            confirm(token, NEW_PASSWORD)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("EMAIL_TOKEN_INVALID"));

            login(DEFAULT_PASSWORD).andExpect(status().isOk());
        }

        @Test
        @DisplayName("없는 토큰은 400")
        void rejectsUnknownToken() throws Exception {
            confirm("never-existed", NEW_PASSWORD).andExpect(status().isBadRequest());
        }

        // 링크를 받아둔 뒤 탈퇴한 경우다
        @Test
        @DisplayName("탈퇴한 계정의 토큰은 404")
        void rejectsTokenOfDeletedUser() throws Exception {
            String token = issuedToken();
            jdbcTemplate.update("UPDATE users SET deleted_at = ? WHERE email = ?", LocalDateTime.now(), EMAIL);

            confirm(token, NEW_PASSWORD)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        }

        @Test
        @DisplayName("10자 미만 비밀번호는 400")
        void rejectsShortPassword() throws Exception {
            confirm(issuedToken(), "Short1!").andExpect(status().isBadRequest());
        }
    }

    // 폼을 다 채운 뒤에 만료를 알려주지 않기 위함이다
    @Nested
    @DisplayName("토큰 확인")
    class Validate {

        @Test
        @DisplayName("쓸 수 있는 토큰이면 가려진 이메일을 돌려주고 토큰은 소진되지 않는다")
        void acceptsUsableToken() throws Exception {
            String token = issuedToken();

            check(token)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("g***@example.com"));

            // 확인은 소비가 아니다
            confirm(token, NEW_PASSWORD).andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("만료된 토큰은 400")
        void rejectsExpiredToken() throws Exception {
            String token = issuedToken();
            expireAllTokens();

            check(token)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("EMAIL_TOKEN_INVALID"));
        }

        @Test
        @DisplayName("이미 쓴 토큰은 400")
        void rejectsUsedToken() throws Exception {
            String token = issuedToken();
            confirm(token, NEW_PASSWORD);

            check(token).andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("없는 토큰은 400")
        void rejectsUnknownToken() throws Exception {
            check("never-existed").andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("가입 인증 토큰은 400")
        void rejectsSignUpToken() throws Exception {
            signUp();

            check(mailSender.lastToken()).andExpect(status().isBadRequest());
        }
    }

    // email_tokens 에 두 종류가 섞인다. 서로의 토큰이 통하면 안 된다
    @Nested
    @DisplayName("토큰 종류 격리")
    class TokenTypeIsolation {

        // 가입 인증 토큰은 가입만 하면 누구나 받는다. 이게 통하면 가입으로 비밀번호를 바꿀 수 있다
        @Test
        @DisplayName("가입 인증 토큰으로는 비밀번호를 재설정할 수 없다")
        void rejectsSignUpTokenOnReset() throws Exception {
            signUp();
            String verifyToken = mailSender.lastToken();

            confirm(verifyToken, NEW_PASSWORD)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("EMAIL_TOKEN_INVALID"));

            login(DEFAULT_PASSWORD).andExpect(status().isOk());
        }

        @Test
        @DisplayName("재설정 토큰으로는 이메일 인증을 통과할 수 없다")
        void rejectsResetTokenOnVerify() throws Exception {
            String resetToken = issuedToken();

            mockMvc.perform(get("/api/auth/verify-email").param("token", resetToken))
                    .andExpect(redirectedUrl("http://localhost:5173/verify-result?status=invalid"));
        }
    }

    // 인증까지 마친 뒤 재설정 메일에서 토큰을 꺼낸다
    private String issuedToken() throws Exception {
        signUpAndVerify();
        return requestAndTakeToken();
    }

    // 재설정은 인증을 마친 계정에만 열린다
    private void signUpAndVerify() throws Exception {
        signUp();
        verify(mailSender.lastToken());
    }

    // 메일 링크를 누른 것과 같다
    private ResultActions verify(String token) throws Exception {
        return mockMvc.perform(get("/api/auth/verify-email").param("token", token));
    }

    private int resetTokenCount() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM email_tokens WHERE token_type = 'PASSWORD_RESET'", Integer.class);
        return count == null ? 0 : count;
    }

    private String requestAndTakeToken() throws Exception {
        mailSender.clear();
        request(EMAIL);
        return mailSender.lastToken();
    }

    private ResultActions request(String email) throws Exception {
        return mockMvc.perform(jsonRequest(post("/api/auth/password-reset"), json("email", email)));
    }

    private ResultActions check(String token) throws Exception {
        return mockMvc.perform(get("/api/auth/password-reset").param("token", token));
    }

    private ResultActions confirm(String token, String newPassword) throws Exception {
        return mockMvc.perform(jsonRequest(
                post("/api/auth/password-reset/confirm"), json("token", token, "newPassword", newPassword)));
    }

    private ResultActions login(String password) throws Exception {
        return mockMvc.perform(jsonRequest(post("/api/auth/login"), json("email", EMAIL, "password", password)));
    }

    private ResultActions signUp() throws Exception {
        mailSender.clear();
        return mockMvc.perform(jsonRequest(
                post("/api/auth/signup"), json("email", EMAIL, "password", DEFAULT_PASSWORD, "nickname", NICKNAME)));
    }

    // 최소 간격을 실제로 기다리지 않고 발급 시각을 과거로 민다
    private void ageTokens() {
        jdbcTemplate.update(
                "UPDATE email_tokens SET created_at = ?", LocalDateTime.now().minusMinutes(10));
    }

    private void expireAllTokens() {
        jdbcTemplate.update(
                "UPDATE email_tokens SET expires_at = ?", LocalDateTime.now().minusDays(1));
    }
}
