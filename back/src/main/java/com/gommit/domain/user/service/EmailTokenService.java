package com.gommit.domain.user.service;

import com.gommit.domain.user.entity.EmailToken;
import com.gommit.domain.user.entity.EmailTokenType;
import com.gommit.domain.user.entity.User;
import com.gommit.domain.user.repository.EmailTokenRepository;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import com.gommit.global.security.SecureTokenProvider;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmailTokenService {

    private final EmailTokenRepository emailTokenRepository;
    private final SecureTokenProvider secureTokenProvider;

    // 토큰 발급
    @Transactional
    public String issue(User user, EmailTokenType type, Duration expiration) {
        String rawToken = secureTokenProvider.generate();
        emailTokenRepository.save(new EmailToken(
                user,
                secureTokenProvider.hash(rawToken),
                type,
                LocalDateTime.now().plus(expiration)));
        return rawToken;
    }

    // 쓸 수 있는 토큰 조회
    @Transactional(readOnly = true)
    public EmailToken findUsable(String rawToken, EmailTokenType type) {
        EmailToken token = emailTokenRepository
                .findByTokenHash(secureTokenProvider.hash(rawToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_TOKEN_INVALID));

        if (token.getTokenType() != type || token.isExpired() || token.isUsed()) {
            throw new BusinessException(ErrorCode.EMAIL_TOKEN_INVALID);
        }
        return token;
    }

    // 토큰 소진
    @Transactional
    public void consume(EmailToken token) {
        if (emailTokenRepository.useIfUnused(token.getId(), LocalDateTime.now()) == 0) {
            throw new BusinessException(ErrorCode.EMAIL_TOKEN_INVALID);
        }
    }

    // 직전 토큰 발급이 최근인지 확인한다
    @Transactional(readOnly = true)
    public boolean hasRecent(Long userId, EmailTokenType type, Duration interval) {
        return emailTokenRepository
                .findFirstByUserIdAndTokenTypeOrderByIdDesc(userId, type)
                .filter(token -> token.getCreatedAt().plus(interval).isAfter(LocalDateTime.now()))
                .isPresent();
    }
}
