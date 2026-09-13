package com.gommit.domain.background.entity;

import com.gommit.global.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "group_backgrounds")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupBackground extends BaseEntity {

    @Column(nullable = false)
    private Long groupId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "background_id", nullable = false)
    private Background background;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GroupBackgroundStatus status;

    @Builder
    public GroupBackground(Long groupId, Background background) {
        this.groupId = groupId;
        this.background = background;
        this.status = GroupBackgroundStatus.INACTIVE;
    }

    public void activate() {
        this.status = GroupBackgroundStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = GroupBackgroundStatus.INACTIVE;
    }
}
