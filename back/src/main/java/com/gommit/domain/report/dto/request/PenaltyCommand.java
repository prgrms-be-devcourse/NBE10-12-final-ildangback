package com.gommit.domain.report.dto.request;

import com.gommit.domain.report.entity.PenaltyType;
import jakarta.validation.constraints.NotNull;

public record PenaltyCommand(
        @NotNull(message = "제재 종류는 필수입니다.") PenaltyType penaltyType, Integer suspensionDays, Integer amount) {}
