package com.gommit.domain.user.dto.response;

import com.gommit.domain.user.entity.User;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record UserProfileResponse(
        Long id,
        String email,
        String nickname,
        String introduction,
        int personalStreak,
        int bestStreak,
        LocalDate lastCheckedInDate,
        LocalDateTime createdAt) {

    public UserProfileResponse(User user) {
        this(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getIntroduction(),
                user.getPersonalStreak(),
                user.getBestStreak(),
                user.getLastCheckedInDate(),
                user.getCreatedAt());
    }
}
