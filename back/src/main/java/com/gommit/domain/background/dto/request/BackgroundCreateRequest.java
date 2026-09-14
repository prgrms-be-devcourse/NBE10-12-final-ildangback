package com.gommit.domain.background.dto.request;

import com.gommit.domain.group.entity.MapType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

public record BackgroundCreateRequest(
        @NotNull MapType mapType,
        @NotBlank @Size(max = 50) String name,
        @Min(0) int price,
        @NotNull MultipartFile image) {}
