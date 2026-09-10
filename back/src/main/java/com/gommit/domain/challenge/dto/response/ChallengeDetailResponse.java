package com.gommit.domain.challenge.dto.response;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeStatus;
import com.gommit.domain.challenge.entity.FrequencyType;
import com.gommit.domain.checkin.entity.CheckInType;
import com.gommit.global.time.DaysOfWeek;
import java.time.LocalDate;
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
        Long ownerId) {
    // groupCurrentStreak 은 저장값이 아니라 조회 시점 보정값(Challenge.groupCurrentStreakAsOf)을 넘겨받는다.
    public ChallengeDetailResponse(Challenge challenge, Long ownerId, int groupCurrentStreak) {
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
                groupCurrentStreak,
                challenge.getGroupBestStreak(),
                getAllowedTypes(challenge),
                ownerId);
    }

    private static List<DaysOfWeek> parseDaysOfWeek(String daysOfWeek) {
        if (daysOfWeek == null || daysOfWeek.isEmpty()) {
            return List.of();
        }

        return Arrays.stream(daysOfWeek.split(","))
                .map(String::trim)
                .map(DaysOfWeek::valueOf)
                .toList();
    }

    private static List<CheckInType> getAllowedTypes(Challenge challenge) {
        return challenge.allowedCheckInTypes();
    }
}
