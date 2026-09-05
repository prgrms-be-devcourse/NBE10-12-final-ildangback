package com.gommit.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @NotBlank(message = "토큰은 필수입니다.") String token,

        @NotBlank(message = "새 비밀번호는 필수입니다.")
        @Size(min = 10, max = 72, message = "비밀번호는 10자 이상 72자 이하여야 합니다.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)[\\x21-\\x7E]+$",
                message = "비밀번호는 영문과 숫자를 모두 포함한 영문 · 숫자 · 특수문자여야 합니다.")
        String newPassword) {}
