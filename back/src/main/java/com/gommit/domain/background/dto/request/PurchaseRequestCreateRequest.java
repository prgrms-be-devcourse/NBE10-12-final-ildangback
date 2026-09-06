package com.gommit.domain.background.dto.request;

import jakarta.validation.constraints.NotNull;

public record PurchaseRequestCreateRequest(
        @NotNull(message = "배경을 선택해 주세요.") Long backgroundId) {}
