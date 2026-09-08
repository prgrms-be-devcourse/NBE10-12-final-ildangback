package com.gommit.domain.challenge.dto.request;

import jakarta.validation.constraints.NotNull;

public record OwnerDelegationRequest(@NotNull Long targetUserId) {}
