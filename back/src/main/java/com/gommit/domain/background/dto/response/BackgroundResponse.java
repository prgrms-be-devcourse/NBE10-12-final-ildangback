package com.gommit.domain.background.dto.response;

import com.gommit.domain.background.entity.Background;
import com.gommit.domain.group.entity.MapType;

public record BackgroundResponse(Long backgroundId, MapType mapType, String name, String imageUrl, int price) {

    public BackgroundResponse(Background background, String imageUrl) {
        this(background.getId(), background.getMapType(), background.getName(), imageUrl, background.getPrice());
    }
}
