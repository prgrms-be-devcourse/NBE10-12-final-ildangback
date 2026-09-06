package com.gommit.domain.checkin.dto.response;

import java.util.List;

// api-spec: CheckIn_DailyLogCursorResponse
public record DailyLogCursorResponse(List<DailyLogResponse> content, CursorPageMeta meta) {}
