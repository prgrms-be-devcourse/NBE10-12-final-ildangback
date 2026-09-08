package com.gommit.domain.item.entity;

import com.gommit.global.base.BaseEntity;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Item extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ItemSlot slot;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private int price;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemImage> images = new ArrayList<>();

    public static Item of(ItemSlot slot, String name, int price) {
        Item item = new Item();
        item.slot = slot;
        item.name = name;
        item.price = price;

        return item;
    }

    public String imageKeyForPose(Pose pose) {
        String defaultKey = null;
        for (ItemImage img : images) {
            if (img.getPose() == pose) return img.getImageKey();
            if (img.getPose() == Pose.DEFAULT) defaultKey = img.getImageKey();
        }
        return defaultKey;
    }
}
