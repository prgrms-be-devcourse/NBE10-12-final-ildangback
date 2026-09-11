package com.gommit.domain.user.service;

import com.gommit.domain.user.entity.OAuthProvider;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import com.gommit.global.security.OAuthProperties;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
@Profile("!test")
public class NaverOAuthClient implements OAuthClient {

    private static final String TOKEN_URI = "https://nid.naver.com/oauth2.0/token";
    private static final String PROFILE_URI = "https://openapi.naver.com/v1/nid/me";
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP =
            new ParameterizedTypeReference<>() {};

    private final OAuthProperties oAuthProperties;
    private final RestClient restClient;

    public NaverOAuthClient(OAuthProperties oAuthProperties, RestClient.Builder restClientBuilder) {
        this.oAuthProperties = oAuthProperties;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.NAVER;
    }

    @Override
    public OAuthUser fetch(OAuthCallback callback) {
        return readProfile(requestAccessToken(callback));
    }

    private String requestAccessToken(OAuthCallback callback) {
        OAuthProperties.Client client = oAuthProperties.naver();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", client.clientId());
        form.add("client_secret", client.clientSecret());
        form.add("code", callback.code());
        form.add("state", callback.state());
        form.add("code_verifier", callback.codeVerifier());

        Map<String, Object> body;
        try {
            body = restClient
                    .post()
                    .uri(TOKEN_URI)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JSON_MAP);
        } catch (RestClientException e) {
            log.warn("네이버 토큰 교환 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.OAUTH_FAILED);
        }

        if (body == null || !(body.get("access_token") instanceof String accessToken)) {
            throw new BusinessException(ErrorCode.OAUTH_FAILED);
        }
        return accessToken;
    }

    // 프로필 조회
    private OAuthUser readProfile(String accessToken) {
        Map<String, Object> body;
        try {
            body = restClient
                    .get()
                    .uri(PROFILE_URI)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(JSON_MAP);
        } catch (RestClientException e) {
            log.warn("네이버 프로필 조회 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.OAUTH_FAILED);
        }

        if (body == null || !(body.get("response") instanceof Map<?, ?> profile)) {
            throw new BusinessException(ErrorCode.OAUTH_FAILED);
        }
        if (!(profile.get("id") instanceof String id) || !(profile.get("email") instanceof String email)) {
            throw new BusinessException(ErrorCode.OAUTH_FAILED);
        }
        return new OAuthUser(id, email);
    }
}
