package com.gommit.domain.background.dto.request;

import jakarta.validation.constraints.NotNull;

public record PurchaseVoteRequest(
        @NotNull(message = "찬반을 선택해 주세요.") Boolean agreed) {}
