package com.gommit.domain.checkin.dto.response;

// api-spec: CheckIn_CheckInResultResponse
// checkIn / currentCount / targetCount / dailyCompleted 만 checkin 이 계산
public record CheckInResultResponse(
        CheckInResponse checkIn,
        int currentCount,
        int targetCount,
        boolean dailyCompleted,
        int earnedUserPoints,
        int currentStreak,
        int groupCompletedCount,
        int groupTotalCount) {}
