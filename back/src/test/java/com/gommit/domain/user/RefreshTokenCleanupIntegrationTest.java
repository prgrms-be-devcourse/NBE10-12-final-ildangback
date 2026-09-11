package com.gommit.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.gommit.domain.user.service.RefreshTokenService;
import com.gommit.support.IntegrationTestSupport;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("RT 만료 행 정리 배치")
class RefreshTokenCleanupIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Test
    @DisplayName("만료된 RT 만 지운다. 폐기됐어도 만료 전이면 남긴다")
    void deleteExpiredRemovesOnlyExpiredRows() {
        Long userId = signUpAndGetUserId();
        LocalDateTime now = LocalDateTime.now();

        // 만료 여부 x 폐기 여부 네 조합. 운영에서 가장 많은 행은 expired-and-revoked 다
        insertToken(userId, "expired-and-revoked", now.minusDays(1), now.minusDays(2));
        insertToken(userId, "expired-not-revoked", now.minusDays(1), null);
        insertToken(userId, "revoked-not-expired", now.plusDays(30), now.minusDays(1));
        insertToken(userId, "active", now.plusDays(30), null);

        int deleted = refreshTokenService.deleteExpired();

        assertThat(deleted).isEqualTo(2);
        assertThat(remainingTokenHashes()).containsExactlyInAnyOrder("revoked-not-expired", "active");
    }

    // 로그인으로 생긴 RT 는 걷어낸다. 이 테스트는 직접 넣은 행만 본다.
    private Long signUpAndGetUserId() {
        loginAs();
        jdbcTemplate.update("DELETE FROM refresh_tokens");
        return jdbcTemplate.queryForObject("SELECT id FROM users LIMIT 1", Long.class);
    }

    private void insertToken(Long userId, String tokenHash, LocalDateTime expiresAt, LocalDateTime revokedAt) {
        jdbcTemplate.update(
                "INSERT INTO refresh_tokens"
                        + " (user_id, token_hash, expires_at, revoked_at, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                userId,
                tokenHash,
                expiresAt,
                revokedAt,
                LocalDateTime.now(),
                LocalDateTime.now());
    }

    private List<String> remainingTokenHashes() {
        return jdbcTemplate.queryForList("SELECT token_hash FROM refresh_tokens", String.class);
    }
}
