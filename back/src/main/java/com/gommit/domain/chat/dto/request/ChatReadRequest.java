package com.gommit.domain.chat.dto.request;

import jakarta.validation.constraints.NotNull;

public record ChatReadRequest(@NotNull Long lastReadMessageId) {}
