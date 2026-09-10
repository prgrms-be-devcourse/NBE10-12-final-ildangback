package com.gommit.domain.checkin.dto.response;

public record CheckInResultResponse(
        CheckInResponse checkIn,
        int currentCount,
        int targetCount,
        boolean dailyCompleted,
        int earnedUserPoints,
        int currentStreak,
        int groupCompletedCount,
        int groupTotalCount) {}
