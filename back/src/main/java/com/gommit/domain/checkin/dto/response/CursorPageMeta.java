package com.gommit.domain.checkin.dto.response;

// api-spec: CheckIn_CursorPageMeta
public record CursorPageMeta(Long nextCursor, boolean hasNext, int size) {}
