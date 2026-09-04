package com.gommit.domain.checkin.dto.response;

import java.util.List;

// api-spec: CheckIn_CheckInCursorResponse
public record CheckInCursorResponse(List<CheckInResponse> content, CursorPageMeta meta) {}
