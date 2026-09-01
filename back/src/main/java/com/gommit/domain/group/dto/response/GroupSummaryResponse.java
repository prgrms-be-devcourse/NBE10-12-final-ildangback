package com.gommit.domain.group.dto.response;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.FrequencyType;
import com.gommit.domain.challenge.entity.Weekday;
import com.gommit.domain.group.entity.ChallengeGroup;
import com.gommit.domain.group.entity.GroupCategory;
import com.gommit.domain.group.entity.GroupStatus;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

public record GroupSummaryResponse(
    Long id,
    String name,
    String description,
    GroupCategory category,
    int currentMembers,
    int maxMembers,
    GroupStatus status,
    Long challengeId,
    LocalDate startDate,
    LocalDate endDate,
    FrequencyType frequencyType,
    Integer frequencyValue,
    List<Weekday> weekdays,
    int dailyCheckInCount
) {
    public GroupSummaryResponse(
        ChallengeGroup group,
        Challenge challenge,
        int currentMembers
    ) {
        this(
            group.getId(),
            group.getName(),
            group.getDescription(),
            group.getCategory(),
            currentMembers,
            group.getMaxMembers(),
            group.getStatus(),
            challenge.getId(),
            challenge.getStartDate(),
            challenge.getEndDate(),
            challenge.getFrequencyType(),
            challenge.getFrequencyValue(),
            parseWeekdays(challenge.getWeekdays()),
            challenge.getDailyCheckInCount()
        );
    }

    private static List<Weekday> parseWeekdays(String weekdays) {
        if (weekdays == null || weekdays.isBlank()) {
            return List.of();
        }

        return Arrays.stream(weekdays.split(","))
            .map(String::trim)
            .map(Weekday::valueOf)
            .toList();
    }
}
