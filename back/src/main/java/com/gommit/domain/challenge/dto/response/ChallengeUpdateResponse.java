package com.gommit.domain.challenge.dto.response;

import com.gommit.domain.challenge.entity.DaysOfWeek;
import com.gommit.domain.challenge.entity.FrequencyType;
import com.gommit.domain.checkin.entity.CheckInType;

import java.time.LocalDate;
import java.util.List;

public record ChallengeUpdateResponse(
    Long id,
    LocalDate startDate,
    LocalDate endDate,
    FrequencyType frequencyType,
    Integer frequencyValue,
    List<DaysOfWeek> daysOfWeek,
    Integer dailyCheckInCount,
    List<CheckInType> allowedTypes
) {
}
