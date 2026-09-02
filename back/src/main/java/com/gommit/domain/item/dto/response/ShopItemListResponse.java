package com.gommit.domain.item.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class ShopItemListResponse {
    private List<ShopItemResponse> content;

    public ShopItemListResponse(List<ShopItemResponse> content) {
        this.content = content;
    }
}
