package com.gommit.domain.item.dto.response;

import com.gommit.domain.item.entity.ItemSlot;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class ItemPurchaseResponse {
    private Long userItemId;
    private Long itemId;
    private LocalDateTime purchasedAt;
    private int balance;
    private ItemSlot equippedSlot;

    public ItemPurchaseResponse(Long userItemId, Long itemId, LocalDateTime purchasedAt, int balance, ItemSlot equippedSlot) {
        this.userItemId = userItemId;
        this.itemId = itemId;
        this.purchasedAt = purchasedAt;
        this.balance = balance;
        this.equippedSlot = equippedSlot;
    }
}
