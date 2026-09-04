package com.gommit.domain.checkin.dto.response;

// api-spec: CheckIn_MyCheckInPageMeta. CheckIn_CursorPageMeta 를 allOf 로 확장해 totalCount 추가.
public record MyCheckInPageMeta(Long nextCursor, boolean hasNext, int size, long totalCount) {}
