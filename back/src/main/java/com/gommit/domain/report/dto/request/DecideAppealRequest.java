package com.gommit.domain.report.dto.request;

import jakarta.validation.constraints.NotNull;

public record DecideAppealRequest(
        @NotNull(message = "인용 여부는 필수입니다.") Boolean accept) {}
