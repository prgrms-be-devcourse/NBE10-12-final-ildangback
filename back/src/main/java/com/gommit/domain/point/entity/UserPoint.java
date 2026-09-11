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

    // 챌린지 중도 탈퇴 회수액 중 잔액 부족으로 당장 못 깎은 나머지. 0 이상만 갖는다.
    // 다음 reward() 때 새로 들어오는 금액에서 이만큼 먼저 갚고 남은 만큼만 잔액에 더한다.
    @Column(nullable = false)
    private int pendingDeduction;

    private UserPoint(Long userId, int balance) {
        this.userId = userId;
        this.balance = balance;
        this.pendingDeduction = 0;
    }

    public static UserPoint init(Long userId) {
        return new UserPoint(userId, 0);
    }

    public void add(int amount) {
        this.balance += amount;
    }

    /**
     * 챌린지 회수(recoverChallengePoints)에서 쓴다. amount(회수해야 할 금액)만큼 잔액에서
     * 깎되 0 밑으로는 안 내려간다 - 모자란 만큼은 pendingDeduction에 쌓아서 다음에 갚는다.
     * 실제로 지금 깎인 금액(히스토리에 남길 값, 0일 수 있음)을 돌려준다.
     */
    public int deductUpToBalance(int amount) {
        int actual = Math.min(amount, balance);
        balance -= actual;
        pendingDeduction += amount - actual;
        return actual;
    }

    /**
     * reward()가 point.add(amount)로 적립을 잔액에 다 반영한 뒤에 부른다. 그 시점 잔액이
     * 허용하는 한도 안에서 pendingDeduction을 갚는다(잔액이 다시 음수로 내려가지 않게).
     * 실제로 갚은 금액(히스토리에 남길 음수 차감분의 절댓값, 0일 수 있음)을 돌려준다.
     */
    public int settlePendingDeduction() {
        int settled = Math.min(pendingDeduction, balance);
        pendingDeduction -= settled;
        balance -= settled;
        return settled;
    }
}
