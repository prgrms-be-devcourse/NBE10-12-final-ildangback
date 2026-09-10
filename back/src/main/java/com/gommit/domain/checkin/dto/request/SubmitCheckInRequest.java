package com.gommit.domain.checkin.dto.request;

import com.gommit.domain.checkin.entity.CheckInType;

public record SubmitCheckInRequest(CheckInType checkInType, String memo) {}
