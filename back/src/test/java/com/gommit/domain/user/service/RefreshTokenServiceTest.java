package com.gommit.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gommit.domain.user.UserFixture;
import com.gommit.domain.user.entity.RefreshToken;
import com.gommit.domain.user.entity.User;
import com.gommit.domain.user.repository.RefreshTokenRepository;
import com.gommit.domain.user.repository.UserRepository;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import com.gommit.global.security.AuthTokenProperties;
import com.gommit.global.security.SecureTokenProvider;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenService")
class RefreshTokenServiceTest {

    private static final Duration GRACE = Duration.ofSeconds(30);

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Spy
    private SecureTokenProvider secureTokenProvider = new SecureTokenProvider();

    @Spy
    private AuthTokenProperties authTokenProperties = new AuthTokenProperties(
            "test-secret-key-that-is-long-enough-32",
            new AuthTokenProperties.AccessToken(Duration.ofMinutes(15)),
            new AuthTokenProperties.RefreshToken(Duration.ofDays(30), GRACE));

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @Test
    @DisplayName("발급하면 원문을 돌려주고 DB 에는 해시만 저장한다")
    void issueStoresHashAndReturnsRaw() {
        User user = UserFixture.user();

        String rawToken = refreshTokenService.issue(user);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(rawToken).isNotBlank();
        assertThat(captor.getValue().getTokenHash())
                .as("원문 저장 금지")
                .isNotEqualTo(rawToken)
                .isNotBlank();
    }

    @Test
    @DisplayName("발급할 때마다 다른 원문이 나온다")
    void issueReturnsDifferentTokenEachTime() {
        User user = UserFixture.user();

        assertThat(refreshTokenService.issue(user)).isNotEqualTo(refreshTokenService.issue(user));
    }

    @Test
    @DisplayName("이미 폐기됐거나 없는 RT 로 로그아웃해도 조용히 넘어간다")
    void revokeIsIdempotent() {
        given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.empty());

        refreshTokenService.revoke("gone");

        verify(refreshTokenRepository, never()).revokeIfActive(anyLong(), any());
    }

    // 원문을 알 수 없으므로(해시는 서비스 내부에서 계산한다) 어떤 해시로 조회하든 같은 토큰을 돌려주게 두고, 발급 API 로 얻은 원문을 그대로 검증에 넣는다
    private String givenStoredToken(User user, LocalDateTime expiresAt) {
        String raw = refreshTokenService.issue(user);
        given(refreshTokenRepository.findByTokenHashForUpdate(anyString()))
                .willReturn(Optional.of(UserFixture.refreshToken(7L, user, "hash", expiresAt)));
        return raw;
    }

    private String givenRotatedToken(User user, LocalDateTime rotatedAt) {
        String raw = refreshTokenService.issue(user);
        given(refreshTokenRepository.findByTokenHashForUpdate(anyString()))
                .willReturn(Optional.of(UserFixture.rotatedRefreshToken(7L, user, "hash", rotatedAt)));
        return raw;
    }

    private String givenRevokedToken(User user, LocalDateTime revokedAt) {
        String raw = refreshTokenService.issue(user);
        given(refreshTokenRepository.findByTokenHashForUpdate(anyString()))
                .willReturn(Optional.of(UserFixture.revokedRefreshToken(7L, user, "hash", revokedAt)));
        return raw;
    }

    private void assertRefreshTokenInvalid(String rawToken) {
        assertThatThrownBy(() -> refreshTokenService.rotate(rawToken))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID);
    }

    @Nested
    @DisplayName("rotate")
    class Rotate {

        @Test
        @DisplayName("유효한 RT 는 로테이션 처리되고 소유자가 반환된다")
        void rotateMarksRotatedAndReturnsOwner() {
            User user = UserFixture.user();
            String raw = givenStoredToken(user, LocalDateTime.now().plusDays(30));
            given(refreshTokenRepository.rotateIfActive(anyLong(), any())).willReturn(1);
            given(userRepository.findByIdAndDeletedAtIsNull(42L)).willReturn(Optional.of(user));

            assertThat(refreshTokenService.rotate(raw).user()).isEqualTo(user);
            verify(refreshTokenRepository).rotateIfActive(eq(7L), any());
        }

        @Test
        @DisplayName("존재하지 않는 RT 는 401")
        void rotateRejectsUnknownToken() {
            given(refreshTokenRepository.findByTokenHashForUpdate(anyString())).willReturn(Optional.empty());

            assertRefreshTokenInvalid("unknown-token");
        }

        @Test
        @DisplayName("만료된 RT 는 탐지가 아니라 단순 401")
        void rotateRejectsExpiredTokenWithoutRevoking() {
            String raw =
                    givenStoredToken(UserFixture.user(), LocalDateTime.now().minusSeconds(1));

            assertRefreshTokenInvalid(raw);
            verify(refreshTokenRepository, never()).rotateIfActive(anyLong(), any());
        }

        @Test
        @DisplayName("로테이션된 지 유예 안이면 재발급된다. 탭 동시 갱신")
        void rotateAcceptsRotatedTokenWithinGrace() {
            User user = UserFixture.user();
            String raw = givenRotatedToken(user, LocalDateTime.now().minusSeconds(GRACE.toSeconds() - 5));
            given(userRepository.findByIdAndDeletedAtIsNull(42L)).willReturn(Optional.of(user));

            assertThat(refreshTokenService.rotate(raw).user()).isEqualTo(user);
        }

        @Test
        @DisplayName("유예를 넘긴 로테이션 RT 는 401 이고, 탐지 비활성 상태에서는 전체 폐기를 하지 않는다")
        void rotateRejectsRotatedTokenPastGrace() {
            String raw =
                    givenRotatedToken(UserFixture.user(), LocalDateTime.now().minusSeconds(GRACE.toSeconds() + 5));

            assertRefreshTokenInvalid(raw);
            verify(refreshTokenRepository, never()).revokeAllByUserId(anyLong(), any());
        }

        @Test
        @DisplayName("세션이 끊긴 RT 는 방금 끊겼어도 401. 유예는 로테이션에만 준다")
        void rotateRejectsRevokedTokenWithoutGrace() {
            String raw = givenRevokedToken(UserFixture.user(), LocalDateTime.now());

            assertRefreshTokenInvalid(raw);
        }

        @Test
        @DisplayName("탈퇴한 계정의 RT 는 소유자 조회에서 걸러진다")
        void rotateRejectsTokenOfDeletedAccount() {
            String raw =
                    givenStoredToken(UserFixture.user(), LocalDateTime.now().plusDays(30));
            given(refreshTokenRepository.rotateIfActive(anyLong(), any())).willReturn(1);
            given(userRepository.findByIdAndDeletedAtIsNull(42L)).willReturn(Optional.empty());

            assertRefreshTokenInvalid(raw);
        }
    }
}
