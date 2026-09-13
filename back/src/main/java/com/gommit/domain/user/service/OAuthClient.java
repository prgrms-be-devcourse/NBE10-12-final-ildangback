package com.gommit.domain.user.service;

import com.gommit.domain.user.entity.OAuthProvider;

public interface OAuthClient {

    OAuthProvider provider();

    // 인가 코드를 프로바이더 정보로 바꾼다
    OAuthUser fetch(OAuthCallback callback);

    record OAuthCallback(String code, String state, String redirectUri, String codeVerifier) {}

    record OAuthUser(String providerUserId, String email) {}
}
