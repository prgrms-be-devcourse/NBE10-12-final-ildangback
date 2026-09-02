package com.gommit.domain.challenge.dto.response;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeStatus;
import com.gommit.domain.challenge.entity.FrequencyType;
import com.gommit.domain.challenge.entity.DaysOfWeek;
import com.gommit.domain.checkin.entity.CheckInType;
import com.gommit.domain.challenge.dto.request.InitialChallengeSettingRequest;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

public record ChallengeSummaryResponse(
    Long id,
    int seqNo,
    ChallengeStatus status,
    LocalDate startDate,
    LocalDate endDate,
    FrequencyType frequencyType,
    Integer frequencyValue,
    List<DaysOfWeek> daysOfWeek,
    int dailyCheckInCount,
    List<CheckInType> allowedTypes
) {
    public ChallengeSummaryResponse(
        Challenge challenge,
        InitialChallengeSettingRequest setting
    ) {
        this(
            challenge.getId(),
            challenge.getSeqNo(),
            challenge.getStatus(),
            challenge.getStartDate(),
            challenge.getEndDate(),
            challenge.getFrequencyType(),
            challenge.getFrequencyValue(),
            setting.daysOfWeek(),
            challenge.getDailyCheckInCount(),
            setting.allowedTypes()
        );
    }

    public ChallengeSummaryResponse(Challenge challenge) {
        this(
            challenge.getId(),
            challenge.getSeqNo(),
            challenge.getStatus(),
            challenge.getStartDate(),
            challenge.getEndDate(),
            challenge.getFrequencyType(),
            challenge.getFrequencyValue(),
            parseDaysOfWeek(challenge.getDaysOfWeek()),
            challenge.getDailyCheckInCount(),
            challenge.isAllowPhoto() ? List.of(CheckInType.PHOTO) : List.of()
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
}
