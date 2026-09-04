package com.gommit.domain.user.service;

import com.gommit.domain.user.entity.OAuthProvider;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import com.gommit.global.security.OAuthProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@Profile("!test")
public class GoogleOAuthClient implements OAuthClient {

    private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP =
            new ParameterizedTypeReference<>() {};
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OAuthProperties oAuthProperties;
    private final RestClient restClient;

    public GoogleOAuthClient(OAuthProperties oAuthProperties, RestClient.Builder restClientBuilder) {
        this.oAuthProperties = oAuthProperties;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.GOOGLE;
    }

    @Override
    public OAuthUser fetch(OAuthCallback callback) {
        return readIdToken(requestIdToken(callback));
    }

    private String requestIdToken(OAuthCallback callback) {
        OAuthProperties.Client client = oAuthProperties.google();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", client.clientId());
        form.add("client_secret", client.clientSecret());
        form.add("code", callback.code());
        form.add("redirect_uri", callback.redirectUri());
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
            log.warn("구글 토큰 교환 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.OAUTH_FAILED);
        }

        if (body == null || !(body.get("id_token") instanceof String idToken)) {
            throw new BusinessException(ErrorCode.OAUTH_FAILED);
        }
        return idToken;
    }

    // ID 토큰에서 사용자 정보 추출
    private OAuthUser readIdToken(String idToken) {
        Map<?, ?> claims;
        try {
            String[] parts = idToken.split("\\.");
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            claims = MAPPER.readValue(payload, Map.class);
        } catch (RuntimeException e) {
            log.warn("구글 ID 토큰 해석 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.OAUTH_FAILED);
        }

        if (!(claims.get("sub") instanceof String sub) || !(claims.get("email") instanceof String email)) {
            throw new BusinessException(ErrorCode.OAUTH_FAILED);
        }
        return new OAuthUser(sub, email);
    }
}
