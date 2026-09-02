package com.gommit.domain.item.dto.response;

import com.gommit.domain.item.entity.Item;
import com.gommit.domain.item.entity.ItemSlot;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ItemResponse {
    private Long id;
    private ItemSlot slot;
    private String name;
    private String imageUrl;
    private int price;

    public ItemResponse(Item item) {
        this.id = item.getId();
        this.slot = item.getSlot();
        this.name = item.getName();
        this.imageUrl = item.getImageUrl();
        this.price = item.getPrice();
    }

}
