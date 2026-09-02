package com.gommit.domain.item.dto.request;

import com.gommit.domain.item.entity.ItemSlot;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

public record ItemCreateRequest(
        @NotNull ItemSlot slot,
        @NotBlank @Size(max = 50) String name,
        @Min(0) int price,
        @NotNull MultipartFile image) {}
