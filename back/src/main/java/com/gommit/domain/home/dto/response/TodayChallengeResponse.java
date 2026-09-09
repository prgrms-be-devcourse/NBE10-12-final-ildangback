package com.gommit.domain.home.dto.response;

import com.gommit.domain.group.entity.GroupCategory;

public record TodayChallengeResponse(
        Long challengeId,
        Long groupId,
        String title,
        GroupCategory category,
        int currentCount,
        int targetCount,
        boolean completed) {}
