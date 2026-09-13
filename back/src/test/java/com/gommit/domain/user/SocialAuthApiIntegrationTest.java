package com.gommit.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gommit.domain.user.dto.request.OAuthLoginRequest;
import com.gommit.domain.user.entity.OAuthProvider;
import com.gommit.domain.user.service.SocialAuthService;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import com.gommit.support.FakeOAuthClient;
import com.gommit.support.IntegrationTestSupport;
import com.gommit.support.RecordingMailSender;
import java.util.ArrayList;
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
import org.springframework.test.web.servlet.ResultActions;

@DisplayName("소셜 로그인 API")
class SocialAuthApiIntegrationTest extends IntegrationTestSupport {

    private static final String EMAIL = "social@example.com";
    private static final String PROVIDER_USER_ID = "1234567890";
    private static final String REDIRECT_URI = "http://localhost:5173/oauth/google/callback";

    @Autowired
    private FakeOAuthClient googleOAuthClient;

    @Autowired
    private FakeOAuthClient naverOAuthClient;

    @Autowired
    private RecordingMailSender mailSender;

    @Autowired
    private SocialAuthService socialAuthService;

    @BeforeEach
    void resetClients() {
        googleOAuthClient.clear();
        naverOAuthClient.clear();
        mailSender.clear();
    }

    @Nested
    @DisplayName("최초 로그인")
    class FirstLogin {

        @Test
        @DisplayName("계정이 만들어지고 newUser 가 true 로 온다")
        void createsAccount() throws Exception {
            googleOAuthClient.willReturn(PROVIDER_USER_ID, EMAIL);

            login("google")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.newUser").value(true))
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                    .andExpect(jsonPath("$.user.email").value(EMAIL));
        }

        // 이 플래그가 소셜 가입자에게는 막는 것이 없다. 그래서 검증하지 않는다 (9-3)
        @Test
        @DisplayName("인증 메일을 보내지 않고 미인증으로 시작한다")
        void sendsNoVerificationMail() throws Exception {
            googleOAuthClient.willReturn(PROVIDER_USER_ID, EMAIL);

            login("google").andExpect(jsonPath("$.user.emailVerified").value(false));

            assertThat(mailSender.lastToken()).isNull();
        }

        // register 의 try/catch (DataIntegrityViolationException) 가 존재하는 이유가 여기서만 확인된다.
        // 사전 검사 existsByEmail 은 동시 요청을 막지 못한다 — 전부 통과한 뒤 UK 가 최종 방어한다.
        // MockMvc 는 스레드 안전을 보장하지 않아 서비스를 직접 호출한다.
        @Test
        @DisplayName("같은 계정으로 동시에 8건이 와도 계정은 하나만 만들어진다")
        void concurrentFirstLoginsCreateOnlyOne() throws Exception {
            googleOAuthClient.willReturn(PROVIDER_USER_ID, EMAIL);
            int parallel = 8;
            CyclicBarrier barrier = new CyclicBarrier(parallel);
            List<Callable<Boolean>> tasks = new ArrayList<>();

            for (int i = 0; i < parallel; i++) {
                tasks.add(() -> {
                    barrier.await();
                    try {
                        socialAuthService.login(OAuthProvider.GOOGLE, oAuthLoginRequest());
                        return true;
                    } catch (BusinessException e) {
                        assertThat(e.getErrorCode())
                                .as("중복은 사전 검사나 UK 위반 둘 중 하나로만 걸러진다")
                                .isIn(ErrorCode.EMAIL_DUPLICATED, ErrorCode.OAUTH_FAILED);
                        return false;
                    }
                });
            }

            ExecutorService pool = Executors.newFixedThreadPool(parallel);
            long succeeded;
            try {
                List<Future<Boolean>> results = pool.invokeAll(tasks);
                succeeded = 0;
                for (Future<Boolean> result : results) {
                    if (result.get()) {
                        succeeded++;
                    }
                }
            } finally {
                pool.shutdownNow();
            }

            // 진 스레드가 승자의 커밋 뒤에 들어오면 재로그인 경로로 성공한다. 상한을 두지 않는다
            assertThat(succeeded).as("적어도 한 건은 로그인에 성공해야 한다").isPositive();
            assertThat(countUsers()).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auth_identities", Integer.class))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("닉네임이 자동으로 만들어진다")
        void generatesNickname() throws Exception {
            googleOAuthClient.willReturn(PROVIDER_USER_ID, EMAIL);

            login("google").andExpect(jsonPath("$.user.nickname").value(Matchers.startsWith("꼬밋러")));
        }

        @Test
        @DisplayName("비밀번호 없이 저장된다")
        void savesWithoutPassword() throws Exception {
            googleOAuthClient.willReturn(PROVIDER_USER_ID, EMAIL);

            login("google");

            assertThat(jdbcTemplate.queryForObject("SELECT password FROM users WHERE email = ?", String.class, EMAIL))
                    .isNull();
        }
    }

    @Nested
    @DisplayName("재로그인")
    class ReturningLogin {

        @Test
        @DisplayName("같은 프로바이더 계정이면 기존 계정으로 들어가고 newUser 가 false 다")
        void reusesAccount() throws Exception {
            googleOAuthClient.willReturn(PROVIDER_USER_ID, EMAIL);
            login("google");

            login("google")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.newUser").value(false));

            assertThat(countUsers()).isEqualTo(1);
        }

        // 프로바이더가 주는 이메일은 바뀔 수 있다. 식별자는 provider_user_id 다 (9-1)
        @Test
        @DisplayName("이메일이 바뀌어도 같은 계정으로 들어간다")
        void identifiesByProviderUserId() throws Exception {
            googleOAuthClient.willReturn(PROVIDER_USER_ID, EMAIL);
            login("google");

            googleOAuthClient.willReturn(PROVIDER_USER_ID, "changed@example.com");
            login("google").andExpect(jsonPath("$.newUser").value(false));

            assertThat(countUsers()).isEqualTo(1);
        }

        @Test
        @DisplayName("프로바이더가 다르면 다른 계정이다")
        void separatesProviders() throws Exception {
            googleOAuthClient.willReturn(PROVIDER_USER_ID, EMAIL);
            login("google");

            naverOAuthClient.willReturn(PROVIDER_USER_ID, "naver@example.com");
            login("naver").andExpect(jsonPath("$.newUser").value(true));

            assertThat(countUsers()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("거절")
    class Rejects {

        // 자동 연결을 하지 않는다. 네이버 이메일은 소유가 확인되지 않는다 (9-1)
        @Test
        @DisplayName("이미 가입된 이메일이면 409")
        void rejectsTakenEmail() throws Exception {
            loginAs(EMAIL, "기존회원");
            googleOAuthClient.willReturn(PROVIDER_USER_ID, EMAIL);

            login("google")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("EMAIL_DUPLICATED"));
        }

        @Test
        @DisplayName("등록되지 않은 리다이렉트 주소는 400")
        void rejectsUnknownRedirectUri() throws Exception {
            googleOAuthClient.willReturn(PROVIDER_USER_ID, EMAIL);

            mockMvc.perform(jsonRequest(
                            post("/api/auth/oauth/google"),
                            json(
                                    "code", "c",
                                    "state", "s",
                                    "redirectUri", "https://evil.example.com/callback",
                                    "codeVerifier", "v")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("OAUTH_REDIRECT_URI_NOT_ALLOWED"));
        }

        @Test
        @DisplayName("프로바이더 호출이 실패하면 401")
        void rejectsProviderFailure() throws Exception {
            googleOAuthClient.willFail();

            login("google")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("OAUTH_FAILED"));
        }

        @Test
        @DisplayName("없는 프로바이더는 400")
        void rejectsUnknownProvider() throws Exception {
            login("kakao")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
        }
    }

    @Nested
    @DisplayName("비밀번호 없는 계정의 다른 경로")
    class WithoutPassword {

        @Test
        @DisplayName("비밀번호 로그인은 401 이고 계정 종류를 알려주지 않는다")
        void rejectsPasswordLogin() throws Exception {
            googleOAuthClient.willReturn(PROVIDER_USER_ID, EMAIL);
            login("google");

            mockMvc.perform(jsonRequest(post("/api/auth/login"), json("email", EMAIL, "password", DEFAULT_PASSWORD)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }

        @Test
        @DisplayName("비밀번호 변경은 401")
        void rejectsPasswordChange() throws Exception {
            String accessToken = socialAccessToken();

            mockMvc.perform(withToken(
                            jsonRequest(
                                    patch("/api/users/me/password"),
                                    json("currentPassword", DEFAULT_PASSWORD, "newPassword", "N3wP@ssword!")),
                            accessToken))
                    .andExpect(status().isUnauthorized());
        }

        // 막으면 영영 탈퇴하지 못한다. AT 가 이미 본인 증명이다
        @Test
        @DisplayName("탈퇴는 비밀번호 확인 없이 된다")
        void allowsAccountDeletion() throws Exception {
            String accessToken = socialAccessToken();

            mockMvc.perform(withToken(jsonRequest(delete("/api/users/me"), json("password", "")), accessToken))
                    .andExpect(status().isNoContent());
        }

        // 재설정으로 비밀번호를 만들 수 있으면 소셜 전용이라는 전제가 깨진다 (12-1 7번)
        @Test
        @DisplayName("재설정 요청은 204 지만 토큰도 메일도 나가지 않는다")
        void issuesNoResetToken() throws Exception {
            googleOAuthClient.willReturn(PROVIDER_USER_ID, EMAIL);
            login("google");
            mailSender.clear();

            mockMvc.perform(jsonRequest(post("/api/auth/password-reset"), json("email", EMAIL)))
                    .andExpect(status().isNoContent());

            assertThat(mailSender.lastToken()).isNull();
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM email_tokens", Integer.class))
                    .isZero();
        }
        // 연결이 남아 있으면 탈퇴한 계정으로 되돌아간다
        @Test
        @DisplayName("탈퇴 후 같은 프로바이더로 들어오면 새 계정이 된다")
        void createsNewAccountAfterDeletion() throws Exception {
            String accessToken = socialAccessToken();
            mockMvc.perform(withToken(jsonRequest(delete("/api/users/me"), json("password", "")), accessToken))
                    .andExpect(status().isNoContent());

            googleOAuthClient.willReturn(PROVIDER_USER_ID, EMAIL);
            login("google")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.newUser").value(true));

            assertThat(countUsers()).isEqualTo(2);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auth_identities", Integer.class))
                    .isEqualTo(1);
        }

        // 소셜 때문에 DTO 제약을 풀었다. 비밀번호 있는 계정까지 열리면 안 된다
        @Test
        @DisplayName("비밀번호 있는 계정은 비밀번호 없이 탈퇴할 수 없다")
        void stillRequiresPasswordWhenSet() throws Exception {
            var tokens = loginAs("member@example.com", "일반회원");

            mockMvc.perform(withToken(jsonRequest(delete("/api/users/me"), "{}"), tokens.accessToken()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
    }

    private String socialAccessToken() throws Exception {
        googleOAuthClient.willReturn(PROVIDER_USER_ID, EMAIL);
        String body = login("google").andReturn().getResponse().getContentAsString();
        return fieldOf(body, "accessToken");
    }

    private ResultActions login(String provider) throws Exception {
        return mockMvc.perform(jsonRequest(
                post("/api/auth/oauth/" + provider),
                json(
                        "code", "auth-code",
                        "state", "state-value",
                        "redirectUri", REDIRECT_URI,
                        "codeVerifier", "code-verifier")));
    }

    private OAuthLoginRequest oAuthLoginRequest() {
        return new OAuthLoginRequest("auth-code", "state-value", REDIRECT_URI, "code-verifier");
    }

    private int countUsers() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        return count == null ? 0 : count;
    }
}
