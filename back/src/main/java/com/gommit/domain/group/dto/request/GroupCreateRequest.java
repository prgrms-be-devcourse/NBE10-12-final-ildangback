package com.gommit.domain.group.dto.request;

import com.gommit.domain.challenge.dto.request.InitialChallengeSettingRequest;
import com.gommit.domain.group.entity.GroupCategory;
import com.gommit.domain.group.entity.MapType;
import com.gommit.domain.group.entity.Visibility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record GroupCreateRequest(
        @NotBlank @Size(min = 1, max = 100) String name,

        @Size(max = 2000) String description,

        @NotNull GroupCategory category,

        @NotNull MapType mapType,

        @NotNull Visibility visibility,

        @NotNull @Min(1) @Max(6) Integer maxMembers,

        @NotNull @Valid InitialChallengeSettingRequest challenge) {}
