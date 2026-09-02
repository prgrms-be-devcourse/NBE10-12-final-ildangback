package com.gommit.domain.item.dto.response;

import com.gommit.domain.item.entity.ItemSlot;
import com.gommit.domain.item.entity.UserItem;

import java.time.LocalDateTime;

public record UserItemResponse(Long id, ItemResponse item, ItemSlot equippedSlot, LocalDateTime purchasedAt) {
    public UserItemResponse(UserItem userItem, ItemResponse item) {
        this(userItem.getId(), item, userItem.getEquippedSlot(), userItem.getCreatedAt());
    }
}
