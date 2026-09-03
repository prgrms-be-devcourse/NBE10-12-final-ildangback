package com.gommit.domain.user.service;

import com.gommit.domain.user.entity.EmailToken;
import com.gommit.domain.user.entity.EmailTokenType;
import com.gommit.domain.user.entity.User;
import com.gommit.domain.user.repository.UserRepository;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.net.URI;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final EmailTokenType TOKEN_TYPE = EmailTokenType.SIGNUP_VERIFY;
    private static final Duration TOKEN_EXPIRATION = Duration.ofHours(24);
    private static final Duration RESEND_MIN_INTERVAL = Duration.ofSeconds(60);
    private static final String LINK_FORMAT = "%s/api/auth/verify-email?token=%s";
    private static final String RESULT_FORMAT = "%s/verify-result?status=%s";
    private static final String SUBJECT = "[꼬밋] 이메일 인증을 완료해 주세요";
    private static final String BODY_FORMAT = "아래 링크를 눌러 이메일 인증을 완료해 주세요.%n%n%s";

    private final EmailTokenService emailTokenService;
    private final UserRepository userRepository;
    private final EmailSender mailSender;

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    @Value("${app.api-base-url}")
    private String apiBaseUrl;

    // 인증 메일 발송
    @Transactional
    public void send(User user) {
        sendMail(user, SUBJECT, BODY_FORMAT);
    }

    // 최소 간격이 지났을 때만 인증 메일 발송
    @Transactional
    public void sendIfDue(User user, String subject, String bodyFormat) {
        if (!emailTokenService.hasRecent(user.getId(), TOKEN_TYPE, RESEND_MIN_INTERVAL)) {
            sendMail(user, subject, bodyFormat);
        }
    }

    // 토큰을 발급해 메일로 보낸다
    private void sendMail(User user, String subject, String bodyFormat) {
        String rawToken = emailTokenService.issue(user, TOKEN_TYPE, TOKEN_EXPIRATION);
        try {
            String link = LINK_FORMAT.formatted(apiBaseUrl, rawToken);
            mailSender.send(user.getEmail(), subject, bodyFormat.formatted(link));
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
        if (emailTokenService.hasRecent(userId, TOKEN_TYPE, RESEND_MIN_INTERVAL)) {
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
        EmailToken token = emailTokenService.findUsable(rawToken, TOKEN_TYPE);
        Long userId = token.getUser().getId();
        emailTokenService.consume(token);

        userRepository
                .findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND))
                .verifyEmail();
    }
}
