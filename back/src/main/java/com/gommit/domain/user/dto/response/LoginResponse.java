package com.gommit.domain.user.dto.response;

public record LoginResponse(String accessToken, String refreshToken, UserProfileResponse user) {

    public LoginResponse(TokenResponse token, UserProfileResponse user) {
        this(token.accessToken(), token.refreshToken(), user);
    }
}
