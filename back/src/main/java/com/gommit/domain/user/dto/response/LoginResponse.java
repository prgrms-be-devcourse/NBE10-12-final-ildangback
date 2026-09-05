package com.gommit.domain.user.dto.response;

public record LoginResponse(String accessToken, String refreshToken, boolean newUser, UserProfileResponse user) {

    public LoginResponse(TokenResponse token, UserProfileResponse user, boolean newUser) {
        this(token.accessToken(), token.refreshToken(), newUser, user);
    }
}
