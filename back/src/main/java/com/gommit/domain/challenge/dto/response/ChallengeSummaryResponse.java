package com.gommit.domain.challenge.dto.response;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeStatus;
import com.gommit.domain.challenge.entity.FrequencyType;
import com.gommit.domain.challenge.entity.Weekday;
import com.gommit.domain.checkin.entity.CheckInType;
import com.gommit.domain.challenge.dto.request.InitialChallengeSettingRequest;

import java.time.LocalDate;
import java.util.List;

public record ChallengeSummaryResponse(
    Long id,
    int seqNo,
    ChallengeStatus status,
    LocalDate startDate,
    LocalDate endDate,
    FrequencyType frequencyType,
    Integer frequencyValue,
    List<Weekday> weekdays,
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
            setting.weekdays(),
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
            parseWeekdays(challenge.getWeekdays()),
            challenge.getDailyCheckInCount(),
            challenge.isAllowPhoto() ? List.of(CheckInType.PHOTO) : List.of()
        );
    }

    private static List<Weekday> parseWeekdays(String weekdays) {
        if(weekdays == null || weekdays.isEmpty()) {
            return null;
        }

        return List.of(weekdays.split(",")).stream().map(String::trim).map(Weekday::valueOf).toList();
    }
}
