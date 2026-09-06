package com.gommit.domain.checkin.dto.response;

public record CursorPageMeta(Long nextCursor, boolean hasNext, int size) {}
