package com.gommit.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gommit.domain.user.entity.RefreshToken;
import com.gommit.domain.user.repository.RefreshTokenRepository;
import com.gommit.domain.user.service.RefreshTokenService;
import com.gommit.global.security.AuthTokenProperties;
import com.gommit.global.security.jwt.JwtProvider;
import com.gommit.support.IntegrationTestSupport;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.ResultActions;

@DisplayName("인증 API")
class AuthApiIntegrationTest extends IntegrationTestSupport {

    private static final String EMAIL = "gommit@example.com";
    private static final String NICKNAME = "꼬밋러";
    private static final String FORGED_SECRET_KEY = "forged-secret-key-for-gommit-testing-minimum-32bytes";

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AuthTokenProperties authTokenProperties;

    // 키와 만료를 바꿔 끼워 실제 발급 경로로 AT 를 만든다. 만료 음수면 이미 만료된 토큰이 나온다.
    private String accessTokenOf(String secretKey, Duration expiration) {
        AuthTokenProperties forged = new AuthTokenProperties(
                secretKey,
                new AuthTokenProperties.AccessToken(expiration),
                new AuthTokenProperties.RefreshToken(Duration.ofDays(30), Duration.ofSeconds(30)));
        return new JwtProvider(forged).issue(1L, "USER");
    }

    private static String bodyOf(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }

    // 유예 경계를 실제 시간으로 기다리지 않기 위해 로테이션 시각을 과거로 민다
    private void backdateRotatedAt(LocalDateTime when) {
        List<RefreshToken> all = refreshTokenRepository.findAll();
        all.forEach(token -> {
            if (token.isRotated()) {
                ReflectionTestUtils.setField(token, "rotatedAt", when);
            }
        });
        refreshTokenRepository.saveAll(all);
        refreshTokenRepository.flush();
    }

    private ResultActions signUp(String email, String nickname) throws Exception {
        return mockMvc.perform(jsonRequest(
                post("/api/auth/signup"),
                json(
                        "email", email,
                        "password", DEFAULT_PASSWORD,
                        "nickname", nickname)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(jsonRequest(post("/api/auth/login"), json("email", email, "password", password)));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(jsonRequest(post("/api/auth/refresh"), json("refreshToken", refreshToken)));
    }

    // ===================================================================
    // 인증 엔드포인트 호출. 공용 기반이 아니라 여기 둔다.
    // ===================================================================

    private ResultActions logout(String accessToken, String refreshToken) throws Exception {
        return mockMvc.perform(
                jsonRequest(withToken(post("/api/auth/logout"), accessToken), json("refreshToken", refreshToken)));
    }

    private ResultActions deleteAccount(String accessToken) throws Exception {
        return mockMvc.perform(
                jsonRequest(withToken(delete("/api/users/me"), accessToken), json("password", DEFAULT_PASSWORD)));
    }

    private ResultActions checkEmail(String email) throws Exception {
        return mockMvc.perform(get("/api/auth/check-email").param("email", email));
    }

    private ResultActions checkNickname(String nickname) throws Exception {
        return mockMvc.perform(get("/api/auth/check-nickname").param("nickname", nickname));
    }

    @Nested
    @DisplayName("로그인")
    class Login {

        @Test
        @DisplayName("성공하면 AT · RT · 내 정보를 함께 준다")
        void returnsTokensAndProfile() throws Exception {
            signUp(EMAIL, NICKNAME);

            login(EMAIL, DEFAULT_PASSWORD)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                    .andExpect(jsonPath("$.user.nickname").value(NICKNAME));
        }

        // 구분하면 이메일 가입 여부가 노출된다(계정 열거). 세 경우가 모두 같은 코드여야 한다.
        @Test
        @DisplayName("계정 없음 · 비밀번호 불일치 · 탈퇴 계정을 구분하지 않는다")
        void doesNotDistinguishFailureCause() throws Exception {
            login("nobody@example.com", DEFAULT_PASSWORD)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

            signUp(EMAIL, NICKNAME);
            login(EMAIL, "wrongpass1")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

            var tokens = loginAs("gone@example.com", "탈퇴예정");
            deleteAccount(tokens.accessToken());
            login("gone@example.com", DEFAULT_PASSWORD)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
    }

    @Nested
    @DisplayName("토큰 재발급")
    class Refresh {

        @Test
        @DisplayName("AT 와 RT 를 모두 새로 주고 옛 RT 는 폐기된다")
        void rotatesBothTokens() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);

            String newRefreshToken =
                    fieldOf(bodyOf(refresh(tokens.refreshToken()).andExpect(status().isOk())), "refreshToken");

            assertThat(newRefreshToken).isNotEqualTo(tokens.refreshToken());
            assertThat(refreshTokenRepository.count()).as("옛 행은 지우지 않는다").isEqualTo(2);
            refresh(newRefreshToken).andExpect(status().isOk());
        }

        // 화면 진입 시 API 를 여러 개 동시에 부르면 갱신도 같이 몰린다. 첫 요청이 RT 를 폐기한 뒤 나머지가 같은 RT 로 도착하는데, 이걸 거부하면 정상
        // 사용자가 로그아웃된다.
        @Test
        @DisplayName("폐기된 지 유예 안이면 통과한다")
        void acceptsRevokedTokenWithinGrace() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            refresh(tokens.refreshToken()).andExpect(status().isOk());

            refresh(tokens.refreshToken()).andExpect(status().isOk());
        }

        // 유예를 실제로 기다리지 않고 revoked_at 을 과거로 밀어 만든다
        @Test
        @DisplayName("유예를 넘긴 로테이션 RT 는 401")
        void rejectsRotatedTokenPastGrace() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            refresh(tokens.refreshToken());

            backdateRotatedAt(LocalDateTime.now().minusMinutes(1));

            refresh(tokens.refreshToken())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
        }

        // 훔친 RT 가 쓰인 것으로 본다. 그 요청만 막으면 최신 RT 를 가진 쪽이 계속 산다
        @Test
        @DisplayName("재사용이 탐지되면 그 사용자의 RT 가 전부 폐기된다")
        void revokesAllOnReuseDetected() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            String otherDevice = fieldOf(bodyOf(login(EMAIL, DEFAULT_PASSWORD)), "refreshToken");
            String rotated = fieldOf(bodyOf(refresh(tokens.refreshToken())), "refreshToken");
            backdateRotatedAt(LocalDateTime.now().minusMinutes(1));

            refresh(tokens.refreshToken()).andExpect(status().isUnauthorized());

            assertThat(jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM refresh_tokens WHERE revoked_at IS NULL", Integer.class))
                    .as("살아 있는 RT 가 남으면 폐기가 롤백된 것이다")
                    .isZero();
            refresh(rotated)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
            refresh(otherDevice).andExpect(status().isUnauthorized());
        }

        // 로그아웃은 재사용이 아니다. 다른 기기까지 끊으면 안 된다
        @Test
        @DisplayName("세션이 끊긴 RT 로 들어와도 다른 기기는 살아 있다")
        void keepsOtherSessionsOnRevokedToken() throws Exception {
            var first = loginAs(EMAIL, NICKNAME);
            var second = login(EMAIL, DEFAULT_PASSWORD);
            String otherDevice = fieldOf(bodyOf(second), "refreshToken");
            logout(first.accessToken(), first.refreshToken()).andExpect(status().isNoContent());

            refresh(first.refreshToken()).andExpect(status().isUnauthorized());

            refresh(otherDevice).andExpect(status().isOk());
        }

        @Test
        @DisplayName("없는 RT 는 401")
        void rejectsUnknownToken() throws Exception {
            refresh("bogus-token")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
        }

        @Test
        @DisplayName("AT 없이 부를 수 있다 — RT 자체가 자격 증명이다")
        void needsNoAccessToken() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);

            refresh(tokens.refreshToken()).andExpect(status().isOk());
        }

        // 비관적 잠금이 실제로 동작하는지 여기서만 확인된다. 두 요청이 동시에 도착하면 양쪽 다 revoked_at IS NULL 을 읽으므로
        // 조회만으로는 막히지 않는다. 진 쪽이 재조회에 실패하면 유예 분기가 null 을 읽어 정상 사용자가 401 을 받는다 — 목으로는
        // 재현되지 않는다.
        @Test
        @DisplayName("같은 RT 로 동시에 8건이 와도 전부 통과한다 — 오탐 없음")
        void concurrentRotationsAllSucceed() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            int parallel = 8;

            CyclicBarrier barrier = new CyclicBarrier(parallel);
            List<Callable<String>> tasks = Collections.nCopies(parallel, () -> {
                barrier.await();
                return refreshTokenService.rotate(tokens.refreshToken()).refreshToken();
            });

            ExecutorService pool = Executors.newFixedThreadPool(parallel);
            try {
                List<Future<String>> results = pool.invokeAll(tasks);
                for (Future<String> result : results) {
                    assertThat(result.get()).as("오탐 없이 새 RT 를 받아야 한다").isNotBlank();
                }
            } finally {
                pool.shutdownNow();
            }
        }
    }

    @Nested
    @DisplayName("로그아웃")
    class Logout {

        @Test
        @DisplayName("전달받은 RT 만 폐기하고 다른 기기의 RT 는 살아 있다")
        void revokesOnlyGivenToken() throws Exception {
            var phone = loginAs(EMAIL, NICKNAME);
            String laptopRefreshToken = fieldOf(bodyOf(login(EMAIL, DEFAULT_PASSWORD)), "refreshToken");

            logout(phone.accessToken(), phone.refreshToken()).andExpect(status().isNoContent());

            refresh(phone.refreshToken()).andExpect(status().isUnauthorized());
            refresh(laptopRefreshToken).andExpect(status().isOk());
        }

        @Test
        @DisplayName("로그아웃한 RT 는 즉시 거부된다")
        void revokedTokenIsRejectedImmediately() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);

            logout(tokens.accessToken(), tokens.refreshToken()).andExpect(status().isNoContent());

            refresh(tokens.refreshToken())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
        }

        @Test
        @DisplayName("이미 폐기된 RT 여도 204 — 멱등")
        void isIdempotent() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);

            logout(tokens.accessToken(), tokens.refreshToken()).andExpect(status().isNoContent());
            logout(tokens.accessToken(), tokens.refreshToken()).andExpect(status().isNoContent());
            logout(tokens.accessToken(), "never-existed").andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("중복 확인")
    class Availability {

        @Test
        @DisplayName("쓰이는 값이면 false, 아니면 true")
        void reportsAvailability() throws Exception {
            checkEmail(EMAIL).andExpect(jsonPath("$.available").value(true));
            loginAs(EMAIL, NICKNAME);

            checkEmail(EMAIL).andExpect(jsonPath("$.available").value(false));
            checkNickname(NICKNAME).andExpect(jsonPath("$.available").value(false));
        }

        @Test
        @DisplayName("쿼리 파라미터 제약 위반도 같은 ErrorResponse 형태로 400 이 된다")
        void rejectsMalformedQuery() throws Exception {
            checkEmail("not-an-email")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                    .andExpect(jsonPath("$.errors[0].field").value("email"));
        }
    }

    @Nested
    @DisplayName("액세스 토큰 검증")
    class AccessTokenValidation {

        @Test
        @DisplayName("만료된 AT 는 401")
        void rejectsExpiredAccessToken() throws Exception {
            String expired = accessTokenOf(authTokenProperties.secretKey(), Duration.ofMinutes(-1));

            mockMvc.perform(withToken(get("/api/users/me"), expired))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("다른 키로 서명한 AT 는 401 — 만료 전이어도 통과하지 않는다")
        void rejectsForgedSignature() throws Exception {
            String forged = accessTokenOf(FORGED_SECRET_KEY, Duration.ofMinutes(15));

            mockMvc.perform(withToken(get("/api/users/me"), forged))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("JWT 형식이 아닌 값은 401")
        void rejectsMalformedToken() throws Exception {
            mockMvc.perform(withToken(get("/api/users/me"), "not-a-jwt"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }
}
