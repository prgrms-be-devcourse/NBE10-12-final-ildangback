package com.gommit.domain.item.dto.response;

import com.gommit.domain.item.entity.ItemSlot;
import com.gommit.domain.item.entity.UserItem;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class UserItemResponse {
    private Long id;
    private ItemResponse item;
    private ItemSlot equippedSlot;
    private LocalDateTime purchasedAt;

    public UserItemResponse(UserItem userItem, ItemResponse item) {
        this.id = userItem.getId();
        this.item = item;
        this.equippedSlot = userItem.getEquippedSlot();
        this.purchasedAt = userItem.getCreatedAt();
    }
}
