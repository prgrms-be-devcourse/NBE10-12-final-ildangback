package com.gommit.domain.challenge.dto.request;

import com.gommit.domain.challenge.entity.ExtensionChoice;
import jakarta.validation.constraints.NotNull;

public record ExtensionChoiceRequest(
    @NotNull ExtensionChoice choice
) {
}
