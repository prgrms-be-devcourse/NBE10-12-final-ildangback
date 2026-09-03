package com.gommit.domain.user.service;

import com.gommit.domain.user.dto.response.PasswordResetTargetResponse;
import com.gommit.domain.user.entity.EmailToken;
import com.gommit.domain.user.entity.EmailTokenType;
import com.gommit.domain.user.entity.User;
import com.gommit.domain.user.repository.EmailTokenRepository;
import com.gommit.domain.user.repository.UserRepository;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import com.gommit.global.security.SecureTokenProvider;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final Duration TOKEN_EXPIRATION = Duration.ofHours(1);
    private static final Duration RESEND_MIN_INTERVAL = Duration.ofSeconds(60);
    private static final String LINK_FORMAT = "%s/reset-password?token=%s";
    private static final String SUBJECT = "[꼬밋] 비밀번호 재설정 안내";
    private static final String BODY_FORMAT = """
            아래 링크를 눌러 새 비밀번호를 설정해 주세요. %d시간 뒤에 만료됩니다.

            %s

            요청한 적이 없다면 이 메일을 무시해 주세요. 비밀번호는 바뀌지 않습니다.""";
    private static final String VERIFY_FIRST_SUBJECT = "[꼬밋] 비밀번호 재설정 전에 이메일 인증이 필요합니다";
    private static final String VERIFY_FIRST_BODY_FORMAT = """
            비밀번호 재설정을 요청하셨습니다. 먼저 아래 링크로 이메일 인증을 완료해 주세요.

            %s

            인증을 마친 뒤 비밀번호 재설정을 다시 요청하시면 재설정 링크를 보내 드립니다.

            요청한 적이 없다면 이 메일을 무시해 주세요.""";

    private final EmailTokenRepository emailTokenRepository;
    private final UserRepository userRepository;
    private final SecureTokenProvider secureTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final EmailVerificationService emailVerificationService;
    private final EmailSender mailSender;

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    // 재설정 메일 발송
    @Transactional
    public void request(String email) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(email).orElse(null);
        if (user == null) {
            return;
        }

        // 미인증이면 재설정 대신 인증 메일을 보낸다
        if (!user.isEmailVerified()) {
            emailVerificationService.sendIfDue(user, VERIFY_FIRST_SUBJECT, VERIFY_FIRST_BODY_FORMAT);
            return;
        }

        if (hasRecentToken(user.getId())) {
            return;
        }

        String rawToken = issue(user);
        try {
            String link = LINK_FORMAT.formatted(frontendBaseUrl, rawToken);
            mailSender.send(user.getEmail(), SUBJECT, BODY_FORMAT.formatted(TOKEN_EXPIRATION.toHours(), link));
        } catch (RuntimeException e) {
            log.warn("재설정 메일 발송 실패: userId={}, {}", user.getId(), e.getMessage());
        }
    }

    // 재설정 대상 조회
    @Transactional(readOnly = true)
    public PasswordResetTargetResponse findTarget(String rawToken) {
        return new PasswordResetTargetResponse(findUsableToken(rawToken).getUser());
    }

    // 새 비밀번호 설정
    @Transactional
    public void reset(String rawToken, String newPassword) {
        EmailToken token = findUsableToken(rawToken);
        Long userId = token.getUser().getId();
        if (emailTokenRepository.useIfUnused(token.getId(), LocalDateTime.now()) == 0) {
            throw new BusinessException(ErrorCode.EMAIL_TOKEN_INVALID);
        }

        User user = userRepository
                .findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.changePassword(passwordEncoder.encode(newPassword));

        refreshTokenService.revokeAll(userId);
    }

    // 쓸 수 있는 재설정 토큰 조회
    private EmailToken findUsableToken(String rawToken) {
        EmailToken token = emailTokenRepository
                .findByTokenHash(secureTokenProvider.hash(rawToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_TOKEN_INVALID));

        if (token.getTokenType() != EmailTokenType.PASSWORD_RESET || token.isExpired() || token.isUsed()) {
            throw new BusinessException(ErrorCode.EMAIL_TOKEN_INVALID);
        }
        return token;
    }

    // 토큰 발급
    private String issue(User user) {
        String rawToken = secureTokenProvider.generate();
        emailTokenRepository.save(new EmailToken(
                user,
                secureTokenProvider.hash(rawToken),
                EmailTokenType.PASSWORD_RESET,
                LocalDateTime.now().plus(TOKEN_EXPIRATION)));
        return rawToken;
    }

    // 직전 재설정 메일이 최근인지 확인한다
    private boolean hasRecentToken(Long userId) {
        return emailTokenRepository
                .findFirstByUserIdAndTokenTypeOrderByIdDesc(userId, EmailTokenType.PASSWORD_RESET)
                .filter(token -> token.getCreatedAt().plus(RESEND_MIN_INTERVAL).isAfter(LocalDateTime.now()))
                .isPresent();
    }
}
