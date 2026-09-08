package com.gommit.domain.challenge.dto.response;

public record ChallengeStatusResponse(
        ChallengeDetailResponse challenge,
        int currentDay,
        int totalDays,
        int participantCount,
        double periodProgressRate,
        boolean isCheckInDay,
        int myCurrentCount,
        boolean myCompleted,
        boolean extensionAvailable // TODO 연장 가능 조건 정책 확정 후 계산
        ) {}
