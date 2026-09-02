package com.gommit.domain.item.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ShopItemResponse {
    private ItemResponse item;
    private boolean owned;
    private boolean equipped;

    public ShopItemResponse(ItemResponse item, boolean owned, boolean equipped) {
        this.item = item;
        this.owned = owned;
        this.equipped = equipped;
    }
}
