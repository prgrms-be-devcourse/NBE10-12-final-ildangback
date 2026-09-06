package com.gommit.domain.background.entity;

import com.gommit.domain.group.entity.MapType;
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
@Table(name = "backgrounds")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Background extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MapType mapType;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 255)
    private String imageKey;

    @Column(nullable = false)
    private int price;

    @Builder
    public Background(MapType mapType, String name, String imageKey, int price) {
        this.mapType = mapType;
        this.name = name;
        this.imageKey = imageKey;
        this.price = price;
    }
}
