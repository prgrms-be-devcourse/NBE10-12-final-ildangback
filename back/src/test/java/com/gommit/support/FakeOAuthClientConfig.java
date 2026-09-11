package com.gommit.support;

import com.gommit.domain.user.entity.OAuthProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test")
public class FakeOAuthClientConfig {

    @Bean
    public FakeOAuthClient googleOAuthClient() {
        return new FakeOAuthClient(OAuthProvider.GOOGLE);
    }

    @Bean
    public FakeOAuthClient naverOAuthClient() {
        return new FakeOAuthClient(OAuthProvider.NAVER);
    }
}
