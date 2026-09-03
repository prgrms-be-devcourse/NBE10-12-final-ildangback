package com.gommit.domain.user.service;

import com.gommit.domain.user.dto.request.LoginRequest;
import com.gommit.domain.user.dto.request.SignUpRequest;
import com.gommit.domain.user.dto.response.LoginResponse;
import com.gommit.domain.user.dto.response.TokenResponse;
import com.gommit.domain.user.dto.response.UserProfileResponse;
import com.gommit.domain.user.dto.response.UserSummaryResponse;
import com.gommit.domain.user.entity.User;
import com.gommit.domain.user.repository.UserRepository;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import com.gommit.global.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    // 회원가입
    @Transactional
    public UserSummaryResponse signUp(SignUpRequest request) {
        String email = request.email();
        String nickname = request.nickname();

        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }
        if (userRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
        }

        User user = new User(email, passwordEncoder.encode(request.password()), nickname);
        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ACCOUNT_INFO_DUPLICATED);
        }

        emailVerificationService.send(user);
        return new UserSummaryResponse(user);
    }

    // 로그인
    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository
                .findByEmailAndDeletedAtIsNull(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        TokenResponse tokens = new TokenResponse(issueAccessToken(user), refreshTokenService.issue(user));
        return new LoginResponse(tokens, new UserProfileResponse(user));
    }

    // 토큰 재발급
    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        RefreshTokenService.RotateResult rotated = refreshTokenService.rotate(rawRefreshToken);
        return new TokenResponse(issueAccessToken(rotated.user()), rotated.refreshToken());
    }

    // 로그아웃
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    // AT 발급
    private String issueAccessToken(User user) {
        return jwtProvider.issue(user.getId(), user.getRole().name());
    }
}
