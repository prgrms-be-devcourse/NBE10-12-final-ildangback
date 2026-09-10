package com.gommit.domain.item.dto.response;

import com.gommit.domain.item.entity.Item;
import com.gommit.domain.item.entity.ItemSlot;

public record ItemResponse(Long id, ItemSlot slot, String name, String imageUrl, int price) {
    public ItemResponse(Item item, String imageUrl) {
        this(item.getId(), item.getSlot(), item.getName(), imageUrl, item.getPrice());
    }
}
