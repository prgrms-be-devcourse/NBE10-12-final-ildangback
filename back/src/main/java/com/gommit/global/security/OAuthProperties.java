package com.gommit.global.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "oauth")
public record OAuthProperties(
        @NotEmpty List<String> allowedRedirectUris,
        @Valid @NotNull Client google,
        @Valid @NotNull Client naver) {

    public record Client(String clientId, String clientSecret) {}

    public boolean isAllowedRedirectUri(String redirectUri) {
        return allowedRedirectUris.contains(redirectUri);
    }
}
