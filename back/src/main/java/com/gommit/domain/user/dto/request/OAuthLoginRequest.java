package com.gommit.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;

public record OAuthLoginRequest(
        @NotBlank(message = "인가 코드는 필수입니다.") String code,
        @NotBlank(message = "state 는 필수입니다.") String state,
        @NotBlank(message = "리다이렉트 주소는 필수입니다.") String redirectUri,
        @NotBlank(message = "code_verifier 는 필수입니다.") String codeVerifier) {}
