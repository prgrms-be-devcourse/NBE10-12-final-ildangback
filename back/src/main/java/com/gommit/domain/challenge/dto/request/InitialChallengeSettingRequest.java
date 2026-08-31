package com.gommit.domain.challenge.dto.request;

import com.gommit.domain.challenge.entity.FrequencyType;
import com.gommit.domain.challenge.entity.Weekday;
import com.gommit.domain.checkin.entity.CheckInType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record InitialChallengeSettingRequest(

    @NotNull
    LocalDate startDate,

    @NotNull
    LocalDate endDate,

    @NotNull
    FrequencyType frequencyType,

    @Min(2)
    @Max(7)
    Integer frequencyValue,

    List<Weekday> weekdays,

    @NotNull
    @Min(1)
    @Max(10)
    Integer dailyCheckInCount,

    @NotEmpty
    List<CheckInType> allowedTypes
) {
}
