package com.gommit.domain.group.dto.response;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.DaysOfWeek;
import com.gommit.domain.challenge.entity.FrequencyType;
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
        List<DaysOfWeek> weekdays,
        int dailyCheckInCount) {
    public GroupSummaryResponse(ChallengeGroup group, Challenge challenge, int currentMembers) {
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
                parseDaysOfWeek(challenge.getDaysOfWeek()),
                challenge.getDailyCheckInCount());
    }

    private static List<DaysOfWeek> parseDaysOfWeek(String daysOfWeek) {
        if (daysOfWeek == null || daysOfWeek.isBlank()) {
            return List.of();
        }

        return Arrays.stream(daysOfWeek.split(","))
                .map(String::trim)
                .map(DaysOfWeek::valueOf)
                .toList();
    }
}
