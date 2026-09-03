package com.gommit.domain.user.service;

import com.gommit.domain.user.entity.EmailToken;
import com.gommit.domain.user.entity.EmailTokenType;
import com.gommit.domain.user.entity.User;
import com.gommit.domain.user.repository.EmailTokenRepository;
import com.gommit.domain.user.repository.UserRepository;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import com.gommit.global.security.SecureTokenProvider;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final Duration TOKEN_EXPIRATION = Duration.ofHours(24);
    private static final Duration RESEND_MIN_INTERVAL = Duration.ofSeconds(60);
    private static final String LINK_FORMAT = "%s/api/auth/verify-email?token=%s";
    private static final String RESULT_FORMAT = "%s/verify-result?status=%s";

    private final EmailTokenRepository emailTokenRepository;
    private final UserRepository userRepository;
    private final SecureTokenProvider secureTokenProvider;
    private final VerificationMailSender mailSender;

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    @Value("${app.api-base-url}")
    private String apiBaseUrl;

    // 인증 메일 발송
    @Transactional
    public void send(User user) {
        String rawToken = issue(user);
        try {
            mailSender.send(user.getEmail(), LINK_FORMAT.formatted(apiBaseUrl, rawToken));
        } catch (RuntimeException e) {
            log.warn("인증 메일 발송 실패: userId={}, {}", user.getId(), e.getMessage());
        }
    }

    // 인증 메일 재발송
    @Transactional
    public void resend(Long userId) {
        User user = userRepository
                .findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.isEmailVerified()) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_VERIFIED);
        }
        if (hasRecentToken(userId)) {
            throw new BusinessException(ErrorCode.EMAIL_RESEND_TOO_SOON);
        }

        send(user);
    }

    // 인증 후 사용자를 보낼 곳
    public URI verifyResultUrl(boolean verified) {
        return URI.create(RESULT_FORMAT.formatted(frontendBaseUrl, verified ? "success" : "invalid"));
    }

    // 토큰 검증
    @Transactional
    public void verify(String rawToken) {
        EmailToken token = emailTokenRepository
                .findByTokenHash(secureTokenProvider.hash(rawToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_TOKEN_INVALID));

        if (token.getTokenType() != EmailTokenType.SIGNUP_VERIFY || token.isExpired() || token.isUsed()) {
            throw new BusinessException(ErrorCode.EMAIL_TOKEN_INVALID);
        }

        Long userId = token.getUser().getId();
        if (emailTokenRepository.useIfUnused(token.getId(), LocalDateTime.now()) == 0) {
            throw new BusinessException(ErrorCode.EMAIL_TOKEN_INVALID);
        }

        userRepository
                .findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND))
                .verifyEmail();
    }

    // 토큰 발급
    private String issue(User user) {
        String rawToken = secureTokenProvider.generate();
        emailTokenRepository.save(new EmailToken(
                user,
                secureTokenProvider.hash(rawToken),
                EmailTokenType.SIGNUP_VERIFY,
                LocalDateTime.now().plus(TOKEN_EXPIRATION)));
        return rawToken;
    }

    // 직전 메일 발송이 최근인지 확인한다
    private boolean hasRecentToken(Long userId) {
        return emailTokenRepository
                .findFirstByUserIdAndTokenTypeOrderByIdDesc(userId, EmailTokenType.SIGNUP_VERIFY)
                .filter(token -> token.getCreatedAt().plus(RESEND_MIN_INTERVAL).isAfter(LocalDateTime.now()))
                .isPresent();
    }
}
