package com.gommit.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.gommit.domain.user.UserFixture;
import com.gommit.domain.user.dto.request.LoginRequest;
import com.gommit.domain.user.dto.request.SignUpRequest;
import com.gommit.domain.user.dto.response.LoginResponse;
import com.gommit.domain.user.entity.User;
import com.gommit.domain.user.repository.UserRepository;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import com.gommit.global.security.jwt.JwtProvider;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("가입하면 토큰 없이 요약 정보만 반환한다")
    void signUpIssuesNoToken() {
        givenNoDuplicate();
        given(passwordEncoder.encode(anyString())).willReturn(UserFixture.ENCODED_PASSWORD);
        given(userRepository.saveAndFlush(any())).willReturn(UserFixture.user(42L, "gommit@example.com", "꼬밋러"));

        var response = authService.signUp(signUpRequest("gommit@example.com", "꼬밋러"));

        assertThat(response.id()).isEqualTo(42L);
        verify(refreshTokenService, org.mockito.Mockito.never()).issue(any());
    }

    @Test
    @DisplayName("이메일이 중복되면 409")
    void signUpRejectsDuplicateEmail() {
        given(userRepository.existsByEmail("gommit@example.com")).willReturn(true);

        assertBusinessException(
                () -> authService.signUp(signUpRequest("gommit@example.com", "꼬밋러")), ErrorCode.EMAIL_DUPLICATED);
    }

    @Test
    @DisplayName("닉네임이 중복되면 409")
    void signUpRejectsDuplicateNickname() {
        given(userRepository.existsByEmail(anyString())).willReturn(false);
        given(userRepository.existsByNickname(anyString())).willReturn(true);

        assertBusinessException(
                () -> authService.signUp(signUpRequest("gommit@example.com", "꼬밋러")), ErrorCode.NICKNAME_DUPLICATED);
    }

    @Test
    @DisplayName("사전 검사를 통과해도 UK 위반은 409 로 변환한다 — 검사와 insert 사이 경합 창")
    void signUpConvertsUniqueViolationToConflict() {
        givenNoDuplicate();
        given(passwordEncoder.encode(anyString())).willReturn(UserFixture.ENCODED_PASSWORD);
        // 어느 UK 가 깨졌는지는 판정에 쓰이지 않으므로 원인을 넣지 않는다.
        given(userRepository.saveAndFlush(any()))
                .willThrow(new DataIntegrityViolationException("could not execute statement"));

        assertBusinessException(
                () -> authService.signUp(signUpRequest("gommit@example.com", "꼬밋러")),
                ErrorCode.ACCOUNT_INFO_DUPLICATED);
    }

    @Test
    @DisplayName("로그인에 성공하면 AT · RT · 내 정보를 함께 반환한다")
    void loginReturnsTokensAndProfile() {
        User user = UserFixture.user();
        given(userRepository.findByEmailAndDeletedAtIsNull("gommit@example.com"))
                .willReturn(Optional.of(user));
        given(passwordEncoder.matches(anyString(), anyString())).willReturn(true);
        given(jwtProvider.issue(anyLong(), anyString())).willReturn("access-token");
        given(refreshTokenService.issue(user)).willReturn("refresh-token");

        LoginResponse response = authService.login(new LoginRequest("gommit@example.com", UserFixture.RAW_PASSWORD));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.user().nickname()).isEqualTo("꼬밋러");
    }

    @Test
    @DisplayName("계정이 없어도 비밀번호가 틀려도 같은 코드로 응답한다 — 계정 열거 차단")
    void loginDoesNotDistinguishFailureCause() {
        given(userRepository.findByEmailAndDeletedAtIsNull(anyString())).willReturn(Optional.empty());

        assertBusinessException(
                () -> authService.login(new LoginRequest("none@example.com", "whatever1")),
                ErrorCode.INVALID_CREDENTIALS);

        User user = UserFixture.user();
        given(userRepository.findByEmailAndDeletedAtIsNull(anyString())).willReturn(Optional.of(user));
        given(passwordEncoder.matches(anyString(), anyString())).willReturn(false);

        assertBusinessException(
                () -> authService.login(new LoginRequest("gommit@example.com", "wrongpass1")),
                ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("재발급은 AT 와 RT 를 모두 새로 준다")
    void refreshIssuesBothTokensAnew() {
        User user = UserFixture.user();
        given(refreshTokenService.rotate("old-rt")).willReturn(new RefreshTokenService.RotateResult(user, "new-rt"));
        given(jwtProvider.issue(anyLong(), anyString())).willReturn("new-at");

        var response = authService.refresh("old-rt");

        assertThat(response.accessToken()).isEqualTo("new-at");
        assertThat(response.refreshToken()).isEqualTo("new-rt");
    }

    @Test
    @DisplayName("로그아웃은 전달받은 RT 1건만 폐기한다")
    void logoutRevokesOnlyGivenToken() {
        authService.logout("rt");

        verify(refreshTokenService).revoke("rt");
    }

    private void givenNoDuplicate() {
        given(userRepository.existsByEmail(anyString())).willReturn(false);
        given(userRepository.existsByNickname(anyString())).willReturn(false);
    }

    private SignUpRequest signUpRequest(String email, String nickname) {
        return new SignUpRequest(email, UserFixture.RAW_PASSWORD, nickname);
    }

    private void assertBusinessException(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }
}
