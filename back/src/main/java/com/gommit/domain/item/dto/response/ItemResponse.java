package com.gommit.domain.item.dto.response;

import com.gommit.domain.item.entity.Item;
import com.gommit.domain.item.entity.ItemSlot;

public record ItemResponse (Long id, ItemSlot slot, String name, String imageUrl, int price) {
    public ItemResponse(Item item) {
        this(item.getId(), item.getSlot(), item.getName(), item.getImageUrl(), item.getPrice());
    }

}
