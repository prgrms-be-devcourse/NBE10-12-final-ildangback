package com.gommit.domain.user.dto.response;

import com.gommit.domain.user.entity.User;

public record UserSummaryResponse(Long id, String email, String nickname) {

    public UserSummaryResponse(User user) {
        this(user.getId(), user.getEmail(), user.getNickname());
    }
}
