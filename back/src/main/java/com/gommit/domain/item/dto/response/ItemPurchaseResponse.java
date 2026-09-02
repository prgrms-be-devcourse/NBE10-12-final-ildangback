package com.gommit.domain.item.dto.response;

import com.gommit.domain.item.entity.ItemSlot;

import java.time.LocalDateTime;

public record ItemPurchaseResponse(Long userItemId, Long itemId, LocalDateTime purchasedAt, int balance, ItemSlot equippedSlot) {

}
