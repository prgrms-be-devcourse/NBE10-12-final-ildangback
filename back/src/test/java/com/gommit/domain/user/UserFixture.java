package com.gommit.domain.user;

import com.gommit.domain.user.entity.RefreshToken;
import com.gommit.domain.user.entity.User;
import java.time.LocalDateTime;
import org.springframework.test.util.ReflectionTestUtils;

public final class UserFixture {

    public static final String RAW_PASSWORD = "P@ssw0rd123";
    public static final String ENCODED_PASSWORD = "{bcrypt}encoded";

    private UserFixture() {}

    public static User user(Long id, String email, String nickname) {
        User user = new User(email, ENCODED_PASSWORD, nickname);
        setId(user, id);
        return user;
    }

    public static User user() {
        return user(42L, "gommit@example.com", "꼬밋러");
    }

    public static RefreshToken refreshToken(Long id, User user, String tokenHash, LocalDateTime expiresAt) {
        RefreshToken token = new RefreshToken(user, tokenHash, expiresAt);
        setId(token, id);
        return token;
    }

    // 로테이션으로 교체된 RT. rotatedAt 을 상대 시간으로 세팅해 유예 분기를 덮는다.
    public static RefreshToken rotatedRefreshToken(Long id, User user, String tokenHash, LocalDateTime rotatedAt) {
        RefreshToken token =
                refreshToken(id, user, tokenHash, LocalDateTime.now().plusDays(30));
        ReflectionTestUtils.setField(token, "rotatedAt", rotatedAt);
        return token;
    }

    // 세션이 끊긴 RT. 유예를 받지 않는다.
    public static RefreshToken revokedRefreshToken(Long id, User user, String tokenHash, LocalDateTime revokedAt) {
        RefreshToken token =
                refreshToken(id, user, tokenHash, LocalDateTime.now().plusDays(30));
        ReflectionTestUtils.setField(token, "revokedAt", revokedAt);
        return token;
    }

    private static void setId(Object entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
    }
}
