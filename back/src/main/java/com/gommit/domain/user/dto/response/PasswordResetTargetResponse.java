package com.gommit.domain.user.dto.response;

import com.gommit.domain.user.entity.User;

public record PasswordResetTargetResponse(String email) {

    private static final String MASK = "***";

    public PasswordResetTargetResponse(User user) {
        this(mask(user.getEmail()));
    }

    // gommit@example.com -> g***@example.com
    private static String mask(String email) {
        return email.charAt(0) + MASK + email.substring(email.indexOf('@'));
    }
}
