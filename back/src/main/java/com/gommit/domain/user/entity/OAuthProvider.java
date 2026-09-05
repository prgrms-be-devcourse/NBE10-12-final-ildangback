package com.gommit.domain.user.entity;

import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.util.Locale;

public enum OAuthProvider {
    GOOGLE,
    NAVER;

    // URL 은 소문자다. google -> GOOGLE
    public static OAuthProvider from(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
