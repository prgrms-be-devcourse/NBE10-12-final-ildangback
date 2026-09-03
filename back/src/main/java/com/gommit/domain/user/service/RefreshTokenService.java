package com.gommit.domain.user.service;

import com.gommit.domain.user.entity.RefreshToken;
import com.gommit.domain.user.entity.User;
import com.gommit.domain.user.repository.RefreshTokenRepository;
import com.gommit.domain.user.repository.UserRepository;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import com.gommit.global.security.AuthTokenProperties;
import com.gommit.global.security.SecureTokenProvider;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final SecureTokenProvider secureTokenProvider;
    private final AuthTokenProperties authTokenProperties;

    // RT 발급
    @Transactional
    public String issue(User user) {
        String rawToken = secureTokenProvider.generate();
        LocalDateTime expiresAt =
                LocalDateTime.now().plus(authTokenProperties.refreshToken().expiration());
        refreshTokenRepository.save(new RefreshToken(user, secureTokenProvider.hash(rawToken), expiresAt));
        return rawToken;
    }

    // RT 로테이션
    @Transactional
    public RotateResult rotate(String rawToken) {
        User owner = verifyAndRotate(rawToken);
        return new RotateResult(owner, issue(owner));
    }

    // RT 검증 후 로테이션 처리
    private User verifyAndRotate(String rawToken) {
        RefreshToken token = refreshTokenRepository
                .findByTokenHashForUpdate(secureTokenProvider.hash(rawToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));

        LocalDateTime now = LocalDateTime.now();
        if (token.isExpired()) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        Long userId = token.getUser().getId();

        if (token.isRevoked()) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        if (!token.isRotated()) {
            refreshTokenRepository.rotateIfActive(token.getId(), now);
            return getActiveUser(userId);
        }

        if (isRotatedInGracePeriod(token, now)) {
            return getActiveUser(userId);
        }

        throw reuseException(userId);
    }

    // RT 1건 폐기
    @Transactional
    public void revoke(String rawToken) {
        LocalDateTime now = LocalDateTime.now();
        refreshTokenRepository
                .findByTokenHash(secureTokenProvider.hash(rawToken))
                .ifPresent(token -> refreshTokenRepository.revokeIfActive(token.getId(), now));
    }

    // 사용자의 모든 RT 폐기
    @Transactional
    public void revokeAll(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId, LocalDateTime.now());
    }

    // 매일 03:00에 만료 RT 정리
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    @Transactional
    public int deleteExpired() {
        int deleted = refreshTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        log.info("만료 RT 정리: {}건 삭제", deleted);
        return deleted;
    }

    // 재사용으로 보고 거부
    private BusinessException reuseException(Long userId) {
        log.warn("RT 재사용 판정: userId={}", userId);
        return new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
    }

    // 탈퇴하지 않은 사용자 조회
    private User getActiveUser(Long userId) {
        return userRepository
                .findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));
    }

    // 로테이션 후 유예 시간 이내인지
    private boolean isRotatedInGracePeriod(RefreshToken token, LocalDateTime now) {
        LocalDateTime rotatedAt = token.getRotatedAt();
        if (rotatedAt == null) {
            return false;
        }
        return !now.isAfter(rotatedAt.plus(authTokenProperties.refreshToken().reuseGracePeriod()));
    }

    // 로테이션 결과
    public record RotateResult(User user, String refreshToken) {}
}
