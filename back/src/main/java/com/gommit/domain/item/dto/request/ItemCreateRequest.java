package com.gommit.domain.item.dto.request;

import com.gommit.domain.item.entity.ItemSlot;
import com.gommit.domain.item.entity.Pose;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public record ItemCreateRequest(
        @NotNull ItemSlot slot,
        @NotBlank @Size(max = 50) String name,
        @Min(0) int price,
        @NotEmpty List<Pose> poses,
        @NotEmpty List<MultipartFile> images) {}
