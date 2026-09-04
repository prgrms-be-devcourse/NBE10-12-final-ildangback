package com.gommit.domain.group.dto.response;

import com.gommit.domain.challenge.entity.ChallengeStatus;
import com.gommit.domain.group.entity.GroupCategory;
import com.gommit.domain.group.entity.GroupStatus;

public record MyGroupSummaryResponse(
        Long groupId,
        Long challengeId,
        String name,
        GroupCategory category,
        GroupStatus groupStatus,
        ChallengeStatus challengeStatus,
        int participantCount,
        int currentDay,
        int totalDays,
        double periodProgressRate,
        int todayCheckInCount,
        int dailyCheckInCount,
        boolean todayCompleted) {}
