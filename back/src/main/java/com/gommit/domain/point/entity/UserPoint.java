package com.gommit.domain.point.entity;

import com.gommit.global.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 개인 포인트 잔액 캐시. 유저당 정확히 1행이며, 지급/차감 시 이 행을 잠그고 갱신해서 동시 요청으로 인한 레이스 컨디션을 막는다. 실제 변동
 * 내역은UserPointHistory가 갖는다.
 */
@Entity
@Getter
@Table(name = "user_points")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPoint extends BaseEntity {

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private int balance;

    private UserPoint(Long userId, int balance) {
        this.userId = userId;
        this.balance = balance;
    }

    public static UserPoint init(Long userId) {
        return new UserPoint(userId, 0);
    }

    public void add(int amount) {
        this.balance += amount;
    }
}
