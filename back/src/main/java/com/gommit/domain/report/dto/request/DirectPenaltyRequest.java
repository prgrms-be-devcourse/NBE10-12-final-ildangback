package com.gommit.domain.report.dto.request;

import com.gommit.domain.report.entity.Report;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record DirectPenaltyRequest(
        @NotBlank(message = "제재 대상 이메일은 필수입니다.") @Email(message = "이메일 형식이 아닙니다.")
        String email,

        @NotBlank(message = "제재 사유는 필수입니다.") @Size(max = Report.DETAIL_MAX_LENGTH, message = "사유는 500자를 넘을 수 없습니다.")
        String detail,

        @NotEmpty(message = "제재를 하나 이상 선택해야 합니다.") @Valid List<PenaltyCommand> penalties) {}
