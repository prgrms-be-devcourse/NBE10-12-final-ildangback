package com.gommit.domain.user.service;

import com.gommit.domain.user.dto.request.OAuthLoginRequest;
import com.gommit.domain.user.dto.response.LoginResponse;
import com.gommit.domain.user.dto.response.TokenResponse;
import com.gommit.domain.user.dto.response.UserProfileResponse;
import com.gommit.domain.user.entity.AuthIdentity;
import com.gommit.domain.user.entity.OAuthProvider;
import com.gommit.domain.user.entity.User;
import com.gommit.domain.user.repository.AuthIdentityRepository;
import com.gommit.domain.user.repository.UserRepository;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import com.gommit.global.security.OAuthProperties;
import com.gommit.global.security.jwt.JwtProvider;
import java.security.SecureRandom;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SocialAuthService {

    private static final String NICKNAME_PREFIX = "꼬밋러";
    private static final int NICKNAME_SUFFIX_BOUND = 100_000_000;
    private static final int NICKNAME_ATTEMPTS = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<OAuthProvider, OAuthClient> clients = new EnumMap<>(OAuthProvider.class);
    private final UserRepository userRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtProvider jwtProvider;
    private final OAuthProperties oAuthProperties;

    public SocialAuthService(
            List<OAuthClient> oAuthClients,
            UserRepository userRepository,
            AuthIdentityRepository authIdentityRepository,
            RefreshTokenService refreshTokenService,
            JwtProvider jwtProvider,
            OAuthProperties oAuthProperties) {
        oAuthClients.forEach(client -> clients.put(client.provider(), client));
        this.userRepository = userRepository;
        this.authIdentityRepository = authIdentityRepository;
        this.refreshTokenService = refreshTokenService;
        this.jwtProvider = jwtProvider;
        this.oAuthProperties = oAuthProperties;
    }

    // 소셜 로그인
    @Transactional
    public LoginResponse login(OAuthProvider provider, OAuthLoginRequest request) {
        if (!oAuthProperties.isAllowedRedirectUri(request.redirectUri())) {
            throw new BusinessException(ErrorCode.OAUTH_REDIRECT_URI_NOT_ALLOWED);
        }

        OAuthClient.OAuthUser oAuthUser = client(provider)
                .fetch(new OAuthClient.OAuthCallback(
                        request.code(), request.state(), request.redirectUri(), request.codeVerifier()));

        return authIdentityRepository
                .findByProviderAndProviderUserId(provider, oAuthUser.providerUserId())
                .map(identity -> issue(identity.getUser(), false))
                .orElseGet(() -> issue(register(provider, oAuthUser), true));
    }

    private OAuthClient client(OAuthProvider provider) {
        OAuthClient client = clients.get(provider);
        if (client == null) {
            throw new BusinessException(ErrorCode.OAUTH_FAILED);
        }
        return client;
    }

    // 신규 가입
    private User register(OAuthProvider provider, OAuthClient.OAuthUser oAuthUser) {
        if (userRepository.existsByEmail(oAuthUser.email())) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }

        try {
            User user = userRepository.saveAndFlush(new User(oAuthUser.email(), generateNickname()));
            authIdentityRepository.saveAndFlush(new AuthIdentity(user, provider, oAuthUser.providerUserId()));
            return user;
        } catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
            throw new BusinessException(ErrorCode.OAUTH_FAILED);
        }
    }

    // 닉네임 자동 생성
    private String generateNickname() {
        for (int i = 0; i < NICKNAME_ATTEMPTS; i++) {
            String nickname = NICKNAME_PREFIX + RANDOM.nextInt(NICKNAME_SUFFIX_BOUND);
            if (!userRepository.existsByNickname(nickname)) {
                return nickname;
            }
        }
        throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
    }

    private LoginResponse issue(User user, boolean newUser) {
        String accessToken = jwtProvider.issue(user.getId(), user.getRole().name());
        TokenResponse tokens = new TokenResponse(accessToken, refreshTokenService.issue(user));
        return new LoginResponse(tokens, new UserProfileResponse(user), newUser);
    }
}
