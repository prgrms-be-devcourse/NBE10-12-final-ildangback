package com.gommit.support;

import com.gommit.domain.user.entity.OAuthProvider;
import com.gommit.domain.user.service.OAuthClient;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;

// 테스트는 프로바이더를 호출하지 않는다. 무엇을 돌려줄지 테스트가 정한다
public class FakeOAuthClient implements OAuthClient {

    private final OAuthProvider provider;
    private OAuthUser next;
    private boolean failOnFetch;

    public FakeOAuthClient(OAuthProvider provider) {
        this.provider = provider;
    }

    public void willReturn(String providerUserId, String email) {
        this.next = new OAuthUser(providerUserId, email);
        this.failOnFetch = false;
    }

    public void willFail() {
        this.failOnFetch = true;
    }

    public void clear() {
        this.next = null;
        this.failOnFetch = false;
    }

    @Override
    public OAuthProvider provider() {
        return provider;
    }

    @Override
    public OAuthUser fetch(OAuthCallback callback) {
        if (failOnFetch || next == null) {
            throw new BusinessException(ErrorCode.OAUTH_FAILED);
        }
        return next;
    }
}
