package com.gommit.domain.group.dto.response;

import com.gommit.domain.group.entity.*;

import java.time.LocalDateTime;

public record GroupResponse(
    Long id,
    String name,
    String description,
    GroupCategory category,
    MapType mapType,
    Visibility visibility,
    int maxMembers,
    int currentMembers,
    Long ownerId,
    GroupStatus status,
    LocalDateTime createdAt
) {
    public GroupResponse (ChallengeGroup group, int currentMembers) {
        this(
            group.getId(),
            group.getName(),
            group.getDescription(),
            group.getCategory(),
            group.getMapType(),
            group.getVisibility(),
            group.getMaxMembers(),
            currentMembers,
            group.getOwnerId(),
            group.getStatus(),
            group.getCreatedAt()
        );
    }
}
