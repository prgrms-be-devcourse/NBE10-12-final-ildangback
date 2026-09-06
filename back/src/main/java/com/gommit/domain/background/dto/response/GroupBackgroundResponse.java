package com.gommit.domain.background.dto.response;

import com.gommit.domain.background.entity.Background;
import com.gommit.domain.group.entity.MapType;

public record GroupBackgroundResponse(Long backgroundId, String name, String imageUrl, MapType mapType) {

    public GroupBackgroundResponse(Background background, String imageUrl) {
        this(background.getId(), background.getName(), imageUrl, background.getMapType());
    }

    public GroupBackgroundResponse(MapType mapType) {
        this(null, null, null, mapType);
    }
}
