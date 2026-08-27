package com.gommit.domain.point.entity;

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
@Table(name = "user_point_histories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPointHistory extends BaseEntity {

    @Column(nullable = false)
    private Long userId;

    private Long challengeId;

    @Column(nullable = false, length = 100)
    private String sourceName;

    @Column(nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserPointReason reason;

    @Column(nullable = false)
    private int balanceAfter;
}
