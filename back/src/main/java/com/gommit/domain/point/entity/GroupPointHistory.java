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
@Table(name = "group_point_histories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupPointHistory extends BaseEntity {

    @Column(nullable = false)
    private Long groupId;

    @Column(nullable = false, length = 100)
    private String sourceName;

    @Column(nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private GroupPointReason reason;

    @Column(nullable = false)
    private int balanceAfter;

    private GroupPointHistory(Long groupId, String sourceName, int amount, GroupPointReason reason, int balanceAfter) {
        this.groupId = groupId;
        this.sourceName = sourceName;
        this.amount = amount;
        this.reason = reason;
        this.balanceAfter = balanceAfter;
    }

    public static GroupPointHistory of(
            Long groupId, String sourceName, int amount, GroupPointReason reason, int balanceAfter) {
        return new GroupPointHistory(groupId, sourceName, amount, reason, balanceAfter);
    }
}
