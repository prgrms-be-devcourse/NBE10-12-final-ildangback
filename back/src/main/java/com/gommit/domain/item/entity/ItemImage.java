package com.gommit.domain.item.entity;

import com.gommit.global.base.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "item_images",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_item_images_item_pose",
                        columnNames = {"item_id", "pose"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemImage extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Pose pose;

    @Column(nullable = false, length = 255)
    private String imageKey;

    public static ItemImage of(Item item, Pose pose, String imageKey) {
        ItemImage img = new ItemImage();
        img.item = item;
        img.pose = pose;
        img.imageKey = imageKey;
        return img;
    }
}
