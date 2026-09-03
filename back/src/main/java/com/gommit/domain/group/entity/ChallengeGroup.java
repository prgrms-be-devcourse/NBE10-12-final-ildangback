package com.gommit.domain.group.entity;

import com.gommit.global.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "challenge_groups")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChallengeGroup extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GroupCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MapType mapType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Visibility visibility;

    @Column(nullable = false)
    private int maxMembers;

    @Column(nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GroupStatus status;

    @Builder
    private ChallengeGroup(
        String name,
        String description,
        GroupCategory category,
        MapType mapType,
        Visibility visibility,
        int maxMembers,
        Long ownerId
    ) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.mapType = mapType;
        this.visibility = visibility;
        this.maxMembers = maxMembers;
        this.ownerId = ownerId;
        this.status = GroupStatus.READY; // 첫 생성 READY
    }

    public void changeOwner(Long ownerId) {
        this.ownerId = ownerId;
    }

    public void activate() {
        this.status = GroupStatus.ACTIVE;
    }

    public void end() {
        this.status = GroupStatus.ENDED;
    }
}
