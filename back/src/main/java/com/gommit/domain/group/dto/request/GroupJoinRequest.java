package com.gommit.domain.group.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GroupJoinRequest(
        @NotBlank @Size(min = 6, max = 6) @Pattern(regexp = "^[A-Z0-9]{6}$")
        String inviteCode) {}
