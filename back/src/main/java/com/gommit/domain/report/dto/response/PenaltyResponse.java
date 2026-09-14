package com.gommit.domain.report.dto.response;

import com.gommit.domain.report.entity.Penalty;
import com.gommit.domain.report.entity.PenaltyType;
import java.time.LocalDateTime;

public record PenaltyResponse(
        Long id, PenaltyType penaltyType, LocalDateTime endsAt, Integer amount, LocalDateTime revokedAt) {

    public PenaltyResponse(Penalty penalty) {
        this(
                penalty.getId(),
                penalty.getPenaltyType(),
                penalty.getEndsAt(),
                penalty.getAmount(),
                penalty.getRevokedAt());
    }
}
