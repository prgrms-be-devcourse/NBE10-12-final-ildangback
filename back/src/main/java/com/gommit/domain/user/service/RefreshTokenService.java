package com.gommit.domain.user.service;

import com.gommit.domain.user.entity.RefreshToken;
import com.gommit.domain.user.entity.User;
import com.gommit.domain.user.repository.RefreshTokenRepository;
import com.gommit.domain.user.repository.UserRepository;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import com.gommit.global.security.AuthTokenProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final String HASH_ALGORITHM = "SHA-256";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final AuthTokenProperties authTokenProperties;

    // RT 발급
    @Transactional
    public String issue(User user) {
        String rawToken = generateRawToken();
        LocalDateTime expiresAt =
                LocalDateTime.now().plus(authTokenProperties.refreshToken().expiration());
        refreshTokenRepository.save(new RefreshToken(user, hash(rawToken), expiresAt));
        return rawToken;
    }

    // RT 로테이션
    @Transactional
    public RotateResult rotate(String rawToken) {
        User owner = verifyAndRevoke(rawToken);
        return new RotateResult(owner, issue(owner));
    }

    // RT 검증 후 폐기
    private User verifyAndRevoke(String rawToken) {
        RefreshToken token = refreshTokenRepository
                .findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));

        LocalDateTime now = LocalDateTime.now();
        if (token.isExpired()) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        Long userId = token.getUser().getId();

        if (!token.isRevoked()) {
            refreshTokenRepository.revokeIfActive(token.getId(), now);
            return findNotDeleted(userId);
        }

        if (isRecentlyRevoked(token, now)) {
            return findNotDeleted(userId);
        }

        throw rejectAsReuse(userId);
    }

    // RT 1건 폐기
    @Transactional
    public void revoke(String rawToken) {
        LocalDateTime now = LocalDateTime.now();
        refreshTokenRepository
                .findByTokenHash(hash(rawToken))
                .ifPresent(token -> refreshTokenRepository.revokeIfActive(token.getId(), now));
    }

    // 사용자의 모든 RT 폐기
    @Transactional
    public void revokeAll(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId, LocalDateTime.now());
    }

    // 재사용으로 보고 거부
    private BusinessException rejectAsReuse(Long userId) {
        log.warn("RT 재사용 판정: userId={}", userId);
        return new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
    }

    // 탈퇴하지 않은 사용자 조회
    private User findNotDeleted(Long userId) {
        return userRepository
                .findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));
    }

    // 폐기 후 유예 시간 이내인지
    private boolean isRecentlyRevoked(RefreshToken token, LocalDateTime now) {
        LocalDateTime revokedAt = token.getRevokedAt();
        if (revokedAt == null) {
            return false;
        }
        return !now.isAfter(revokedAt.plus(authTokenProperties.refreshToken().reuseGracePeriod()));
    }

    // RT 생성
    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // 해시
    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance(HASH_ALGORITHM).digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // 로테이션 결과
    public record RotateResult(User user, String refreshToken) {}
}
