package com.gommit.domain.group.dto.response;

public record CursorMetaResponse(Long nextCursor, boolean hasNext, int size) {}
