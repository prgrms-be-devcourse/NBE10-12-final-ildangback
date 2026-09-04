package com.gommit.domain.item.entity;

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
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "user_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserItem extends BaseEntity {

    @Column(nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ItemSlot equippedSlot;

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public boolean isEquipped() {
        return this.equippedSlot != null;
    }

    public void equip() {
        this.equippedSlot = this.item.getSlot();
    }

    public void unequip() {
        this.equippedSlot = null;
    }

    public static UserItem of(Long userId, Item item) {
        UserItem userItem = new UserItem();
        userItem.userId = userId;
        userItem.item = item;
        return userItem;
    }
}
