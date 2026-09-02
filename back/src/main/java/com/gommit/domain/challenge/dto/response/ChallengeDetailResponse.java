package com.gommit.domain.challenge.dto.response;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeStatus;
import com.gommit.domain.challenge.entity.FrequencyType;
import com.gommit.domain.challenge.entity.DaysOfWeek;
import com.gommit.domain.checkin.entity.CheckInType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record ChallengeDetailResponse(
    Long id,
    Long groupId,
    Integer seqNo,
    LocalDate startDate,
    LocalDate endDate,
    ChallengeStatus status,
    FrequencyType frequencyType,
    Integer frequencyValue,
    List<DaysOfWeek> daysOfWeek,
    Integer dailyCheckInCount,
    Integer requiredDayCount,
    Integer groupCurrentStreak,
    Integer groupBestStreak,
    List<CheckInType> allowedTypes,
    Long ownerId
) {
    public ChallengeDetailResponse(Challenge challenge, Long ownerId) {
        this(
            challenge.getId(),
            challenge.getGroupId(),
            challenge.getSeqNo(),
            challenge.getStartDate(),
            challenge.getEndDate(),
            challenge.getStatus(),
            challenge.getFrequencyType(),
            challenge.getFrequencyValue(),
            parseDaysOfWeek(challenge.getDaysOfWeek()),
            challenge.getDailyCheckInCount(),
            challenge.getRequiredDayCount(),
            challenge.getGroupCurrentStreak(),
            challenge.getGroupBestStreak(),
            getAllowedTypes(challenge),
            ownerId
        );
    }

    private static List<DaysOfWeek> parseDaysOfWeek(String daysOfWeek) {
        if(daysOfWeek == null || daysOfWeek.isEmpty()) {
            return List.of();
        }

        return Arrays.stream(daysOfWeek.split(","))
            .map(String::trim)
            .map(DaysOfWeek::valueOf)
            .toList();
    }

    private static List<CheckInType> getAllowedTypes(Challenge challenge) {
        List<CheckInType> allowedTypes = new ArrayList<>();

        if (challenge.isAllowPhoto()) {
            allowedTypes.add(CheckInType.PHOTO);
        }

        return allowedTypes;
    }
}
