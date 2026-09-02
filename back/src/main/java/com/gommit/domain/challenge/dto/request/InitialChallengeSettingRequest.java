package com.gommit.domain.challenge.dto.request;

import com.gommit.domain.challenge.entity.DaysOfWeek;
import com.gommit.domain.challenge.entity.FrequencyType;
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

    List<DaysOfWeek> daysOfWeek,

    @NotNull
    @Min(1)
    @Max(10)
    Integer dailyCheckInCount,

    @NotEmpty(message = "인증 방식은 최소 1개 이상 선택해야 합니다.")
    List<CheckInType> allowedTypes
) {
}
