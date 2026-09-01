package com.gommit.global.security.jwt;

import com.gommit.global.security.AuthTokenProperties;
import com.gommit.global.security.SecurityUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JwtProvider {

    private static final String CLAIM_ROLE = "role";

    private final SecretKey secretKey;
    private final long expirationSeconds;

    public JwtProvider(AuthTokenProperties properties) {
        this.secretKey = Keys.hmacShaKeyFor(properties.secretKey().getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = properties.accessToken().expiration().getSeconds();
    }

    public String issue(Long userId, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_ROLE, role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(secretKey)
                .compact();
    }

    public SecurityUser parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return new SecurityUser(Long.valueOf(claims.getSubject()), claims.get(CLAIM_ROLE, String.class));

        } catch (JwtException | IllegalArgumentException e) {
            log.debug("AT 검증 실패: {}", e.getClass().getSimpleName());
            return null;
        }
    }
}
