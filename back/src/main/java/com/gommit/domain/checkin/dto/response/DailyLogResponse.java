package com.gommit.domain.checkin.dto.response;

import java.time.LocalDate;

// api-spec: CheckIn_DailyLog
public record DailyLogResponse(Long id, LocalDate businessDate, String videoUrl, int completedCount, int totalCount) {}
