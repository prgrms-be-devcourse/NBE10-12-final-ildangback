package com.gommit.domain.user.controller;

import com.gommit.domain.user.dto.request.LoginRequest;
import com.gommit.domain.user.dto.request.OAuthLoginRequest;
import com.gommit.domain.user.dto.request.PasswordResetConfirmRequest;
import com.gommit.domain.user.dto.request.PasswordResetRequest;
import com.gommit.domain.user.dto.request.RefreshRequest;
import com.gommit.domain.user.dto.request.SignUpRequest;
import com.gommit.domain.user.dto.response.AvailabilityResponse;
import com.gommit.domain.user.dto.response.LoginResponse;
import com.gommit.domain.user.dto.response.PasswordResetTargetResponse;
import com.gommit.domain.user.dto.response.TokenResponse;
import com.gommit.domain.user.dto.response.UserSummaryResponse;
import com.gommit.domain.user.entity.OAuthProvider;
import com.gommit.domain.user.service.AuthService;
import com.gommit.domain.user.service.EmailVerificationService;
import com.gommit.domain.user.service.PasswordResetService;
import com.gommit.domain.user.service.SocialAuthService;
import com.gommit.domain.user.service.UserService;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.security.CurrentUser;
import com.gommit.global.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "사용자 로그인 및 토큰 관리 API")
@Validated
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;
    private final SocialAuthService socialAuthService;

    @Operation(summary = "회원가입")
    @PostMapping("/signup")
    public ResponseEntity<UserSummaryResponse> signUp(@Valid @RequestBody SignUpRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signUp(request));
    }

    @Operation(summary = "로그인")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(summary = "소셜 로그인")
    @PostMapping("/oauth/{provider}")
    public ResponseEntity<LoginResponse> oauthLogin(
            @PathVariable String provider, @Valid @RequestBody OAuthLoginRequest request) {
        return ResponseEntity.ok(socialAuthService.login(OAuthProvider.from(provider), request));
    }

    @Operation(summary = "토큰 재발급")
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @Operation(summary = "로그아웃")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "이메일 중복 확인")
    @GetMapping("/check-email")
    public ResponseEntity<AvailabilityResponse> checkEmail(
            @RequestParam
                    @NotBlank(message = "이메일은 필수입니다.")
                    @Email(message = "이메일 형식이 아닙니다.")
                    @Size(max = 255, message = "이메일은 255자를 넘을 수 없습니다.")
                    String email) {
        return ResponseEntity.ok(new AvailabilityResponse(userService.isEmailAvailable(email)));
    }

    @Operation(summary = "닉네임 중복 확인")
    @GetMapping("/check-nickname")
    public ResponseEntity<AvailabilityResponse> checkNickname(
            @RequestParam
                    @NotBlank(message = "닉네임은 필수입니다.")
                    @Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하여야 합니다.")
                    @Pattern(
                            regexp = "^[가-힣ㄱ-ㅎㅏ-ㅣa-zA-Z0-9]+( [가-힣ㄱ-ㅎㅏ-ㅣa-zA-Z0-9]+)*$",
                            message = "닉네임은 한글·영문·숫자만 쓸 수 있습니다.")
                    String nickname) {
        return ResponseEntity.ok(new AvailabilityResponse(userService.isNicknameAvailable(nickname)));
    }

    @Operation(summary = "이메일 인증")
    @GetMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@RequestParam String token) {
        boolean verified = true;
        try {
            emailVerificationService.verify(token);
        } catch (BusinessException e) {
            verified = false;
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(emailVerificationService.verifyResultUrl(verified))
                .build();
    }

    @Operation(summary = "인증 메일 재발송")
    @PostMapping("/verify-email/resend")
    public ResponseEntity<Void> resendVerificationEmail(@CurrentUser SecurityUser actor) {
        emailVerificationService.resend(actor.getId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "비밀번호 재설정 메일 요청")
    @PostMapping("/password-reset")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.request(request.email());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "비밀번호 재설정 토큰 확인")
    @GetMapping("/password-reset")
    public ResponseEntity<PasswordResetTargetResponse> checkPasswordResetToken(@RequestParam String token) {
        return ResponseEntity.ok(passwordResetService.findTarget(token));
    }

    @Operation(summary = "비밀번호 재설정")
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.reset(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
