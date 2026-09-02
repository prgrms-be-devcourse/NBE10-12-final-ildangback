package com.gommit.domain.item.dto.request;

import com.gommit.domain.item.entity.ItemSlot;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import org.springframework.web.multipart.MultipartFile;

@Getter
public class ItemCreateRequest {
    @NotNull
    private ItemSlot slot;

    @NotBlank @Size(max = 50)
    private String name;

    @Min(0)
    private int price;

    @NotNull
    private MultipartFile image;
}
