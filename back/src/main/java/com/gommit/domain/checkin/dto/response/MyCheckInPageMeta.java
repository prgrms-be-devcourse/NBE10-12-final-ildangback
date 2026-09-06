package com.gommit.domain.checkin.dto.response;

public record MyCheckInPageMeta(Long nextCursor, boolean hasNext, int size, long totalCount) {}
