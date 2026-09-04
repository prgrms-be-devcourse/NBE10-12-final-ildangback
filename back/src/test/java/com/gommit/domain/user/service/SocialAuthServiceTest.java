package com.gommit.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import com.gommit.domain.user.UserFixture;
import com.gommit.domain.user.dto.request.OAuthLoginRequest;
import com.gommit.domain.user.entity.AuthIdentity;
import com.gommit.domain.user.entity.OAuthProvider;
import com.gommit.domain.user.entity.User;
import com.gommit.domain.user.repository.AuthIdentityRepository;
import com.gommit.domain.user.repository.UserRepository;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import com.gommit.global.security.OAuthProperties;
import com.gommit.global.security.jwt.JwtProvider;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@DisplayName("SocialAuthService")
class SocialAuthServiceTest {

    private static final String PROVIDER_USER_ID = "1234567890";
    private static final String EMAIL = "social@example.com";
    private static final String REDIRECT_URI = "http://localhost:5173/oauth/google/callback";

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthIdentityRepository authIdentityRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private JwtProvider jwtProvider;

    private StubOAuthClient googleClient;
    private SocialAuthService socialAuthService;

    @BeforeEach
    void setUp() {
        googleClient = new StubOAuthClient();
        OAuthProperties properties = new OAuthProperties(
                List.of(REDIRECT_URI),
                new OAuthProperties.Client("google-id", "google-secret"),
                new OAuthProperties.Client("naver-id", "naver-secret"));
        socialAuthService = new SocialAuthService(
                List.of(googleClient),
                userRepository,
                authIdentityRepository,
                refreshTokenService,
                jwtProvider,
                properties);
    }

    @Nested
    @DisplayName("거절")
    class Rejects {

        @Test
        @DisplayName("등록되지 않은 리다이렉트 주소는 프로바이더를 부르기 전에 막는다")
        void rejectsUnknownRedirectUri() {
            assertThatThrownBy(() -> socialAuthService.login(
                            OAuthProvider.GOOGLE,
                            new OAuthLoginRequest("code", "state", "https://evil.example.com/cb", "verifier")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.OAUTH_REDIRECT_URI_NOT_ALLOWED);

            assertThat(googleClient.called).isFalse();
        }

        @Test
        @DisplayName("클라이언트가 없는 프로바이더는 401")
        void rejectsProviderWithoutClient() {
            assertThatThrownBy(() -> socialAuthService.login(OAuthProvider.NAVER, request()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.OAUTH_FAILED);
        }
    }

    @Nested
    @DisplayName("신규 가입")
    class Register {

        // 사전 검사 existsByEmail 과 insert 사이의 경합 창. 최종 방어는 UK 다
        @Test
        @DisplayName("사전 검사를 통과해도 UK 위반은 401 로 변환한다")
        void convertsUniqueViolation() {
            given(userRepository.existsByEmail(EMAIL)).willReturn(false);
            given(userRepository.existsByNickname(anyString())).willReturn(false);
            given(userRepository.saveAndFlush(any(User.class))).willReturn(UserFixture.user(1L, EMAIL, "꼬밋러1"));
            willThrow(new DataIntegrityViolationException("uk_auth_identities"))
                    .given(authIdentityRepository)
                    .saveAndFlush(any(AuthIdentity.class));

            assertThatThrownBy(() -> socialAuthService.login(OAuthProvider.GOOGLE, request()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.OAUTH_FAILED);
        }

        @Test
        @DisplayName("닉네임을 정해진 횟수 안에 못 만들면 409")
        void failsWhenNicknameSpaceExhausted() {
            given(userRepository.existsByEmail(EMAIL)).willReturn(false);
            given(userRepository.existsByNickname(anyString())).willReturn(true);

            assertThatThrownBy(() -> socialAuthService.login(OAuthProvider.GOOGLE, request()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.NICKNAME_DUPLICATED);
        }
    }

    @Nested
    @DisplayName("재로그인")
    class ReturningLogin {

        @Test
        @DisplayName("연결된 계정이 있으면 가입 없이 토큰만 발급한다")
        void issuesTokensWithoutRegistering() {
            User user = UserFixture.user(7L, EMAIL, "꼬밋러7");
            given(authIdentityRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, PROVIDER_USER_ID))
                    .willReturn(Optional.of(new AuthIdentity(user, OAuthProvider.GOOGLE, PROVIDER_USER_ID)));
            given(jwtProvider.issue(7L, user.getRole().name())).willReturn("access-token");
            given(refreshTokenService.issue(user)).willReturn("refresh-token");

            assertThat(socialAuthService.login(OAuthProvider.GOOGLE, request()).newUser())
                    .isFalse();
        }
    }

    private OAuthLoginRequest request() {
        return new OAuthLoginRequest("code", "state", REDIRECT_URI, "verifier");
    }

    private static final class StubOAuthClient implements OAuthClient {

        private boolean called;

        @Override
        public OAuthProvider provider() {
            return OAuthProvider.GOOGLE;
        }

        @Override
        public OAuthUser fetch(OAuthCallback callback) {
            called = true;
            return new OAuthUser(PROVIDER_USER_ID, EMAIL);
        }
    }
}
