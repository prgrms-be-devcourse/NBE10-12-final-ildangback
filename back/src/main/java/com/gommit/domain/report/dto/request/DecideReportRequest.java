package com.gommit.domain.report.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record DecideReportRequest(
        @NotNull(message = "승인 여부는 필수입니다.") Boolean accept,
        @Valid List<PenaltyCommand> penalties) {

    public List<PenaltyCommand> penaltiesOrEmpty() {
        return penalties == null ? List.of() : penalties;
    }
}
