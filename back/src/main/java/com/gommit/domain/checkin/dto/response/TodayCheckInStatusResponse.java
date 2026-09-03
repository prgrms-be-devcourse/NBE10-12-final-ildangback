package com.gommit.domain.checkin.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gommit.domain.checkin.entity.CheckInType;
import java.time.LocalDate;
import java.util.List;

// api-spec: CheckIn_TodayCheckInStatus
public record TodayCheckInStatusResponse(
        LocalDate businessDate,
        @JsonProperty("isCheckInDay") boolean isCheckInDay,
        int currentCount,
        int targetCount,
        boolean completed,
        List<CheckInType> allowedTypes) {}
