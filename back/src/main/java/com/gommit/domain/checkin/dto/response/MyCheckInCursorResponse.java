package com.gommit.domain.checkin.dto.response;

import java.util.List;

public record MyCheckInCursorResponse(List<MyCheckInResponse> content, MyCheckInPageMeta meta) {}
