package com.gommit.global.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "auth-token")
public record AuthTokenProperties(
        @NotBlank @Size(min = 32, message = "시크릿 키는 32자 이상이어야 합니다.")
        String secretKey,

        @Valid @NotNull AccessToken accessToken,
        @Valid @NotNull RefreshToken refreshToken) {

    public record AccessToken(@NotNull Duration expiration) {}

    public record RefreshToken(
            @NotNull Duration expiration, @NotNull Duration reuseGracePeriod) {}
}
