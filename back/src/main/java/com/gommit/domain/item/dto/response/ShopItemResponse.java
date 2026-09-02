package com.gommit.domain.item.dto.response;

public record ShopItemResponse (ItemResponse item, boolean owned, boolean equipped) {
}
