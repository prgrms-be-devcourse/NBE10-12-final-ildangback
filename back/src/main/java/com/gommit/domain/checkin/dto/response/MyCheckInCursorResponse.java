package com.gommit.domain.checkin.dto.response;

import java.util.List;

// api-spec: CheckIn_MyCheckInCursorResponse
public record MyCheckInCursorResponse(List<MyCheckInResponse> content, MyCheckInPageMeta meta) {}
