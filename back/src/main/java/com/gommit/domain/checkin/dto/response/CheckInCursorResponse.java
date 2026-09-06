package com.gommit.domain.checkin.dto.response;

import java.util.List;

public record CheckInCursorResponse(List<CheckInResponse> content, CursorPageMeta meta) {}
