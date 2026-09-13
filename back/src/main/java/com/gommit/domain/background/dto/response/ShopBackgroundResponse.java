package com.gommit.domain.background.dto.response;

import com.gommit.domain.background.entity.Background;

public record ShopBackgroundResponse(
        Long backgroundId, String name, String imageUrl, int price, boolean owned, boolean active, boolean voting) {

    public ShopBackgroundResponse(
            Background background, String imageUrl, boolean owned, boolean active, boolean voting) {
        this(background.getId(), background.getName(), imageUrl, background.getPrice(), owned, active, voting);
    }
}
