package com.gommit.domain.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 아닙니다.")
        @Size(max = 255, message = "이메일은 255자를 넘을 수 없습니다.")
        String email) {}
