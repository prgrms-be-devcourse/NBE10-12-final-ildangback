package com.gommit.domain.item.entity;

import com.gommit.global.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
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

    @Column(nullable = false, length = 500)
    private String imageUrl;

    @Column(nullable = false)
    private int price;

    public static Item of(ItemSlot slot, String name, String imageUrl, int price) {
        Item item = new Item();
        item.slot = slot;
        item.name = name;
        item.imageUrl = imageUrl;
        item.price = price;

        return item;
    }
}
