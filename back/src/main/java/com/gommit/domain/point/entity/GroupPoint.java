package com.gommit.domain.point.entity;

import com.gommit.global.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 그룹 포인트 잔액 캐시. 그룹당 정확히 1행이며, 지급/차감 시 이 행을 잠그고 갱신해서 동시 요청으로 인한 레이스 컨디션을 막는다. 실제 변동 내역은
 * GroupPointHistory가 갖는다.
 */
@Entity
@Getter
@Table(name = "group_points")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupPoint extends BaseEntity {

    @Column(nullable = false)
    private Long groupId;

    @Column(nullable = false)
    private int balance;

    private GroupPoint(Long groupId, int balance) {
        this.groupId = groupId;
        this.balance = balance;
    }

    public static GroupPoint init(Long groupId) {
        return new GroupPoint(groupId, 0);
    }

    public void add(int amount) {
        this.balance += amount;
    }
}
