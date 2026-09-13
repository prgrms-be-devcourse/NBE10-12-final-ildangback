package com.gommit.domain.report.dto.request;

import com.gommit.domain.report.entity.Appeal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitAppealRequest(
        @NotBlank(message = "이의제기 내용은 필수입니다.")
        @Size(max = Appeal.CONTENT_MAX_LENGTH, message = "이의제기 내용은 1000자를 넘을 수 없습니다.")
        String content) {}
