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

    private UserPointHistory(
            Long userId, Long challengeId, String sourceName, int amount, UserPointReason reason, int balanceAfter) {
        this.userId = userId;
        this.challengeId = challengeId;
        this.sourceName = sourceName;
        this.amount = amount;
        this.reason = reason;
        this.balanceAfter = balanceAfter;
    }

    public static UserPointHistory of(
            Long userId, Long challengeId, String sourceName, int amount, UserPointReason reason, int balanceAfter) {
        return new UserPointHistory(userId, challengeId, sourceName, amount, reason, balanceAfter);
    }

    // 챌린지와 무관한 변동(아이템 구매 등)용 팩토리. challengeId 자리에 null을 직접
    // 넘기지 않도록 분리했다.
    public static UserPointHistory ofWithoutChallenge(
            Long userId, String sourceName, int amount, UserPointReason reason, int balanceAfter) {
        return new UserPointHistory(userId, null, sourceName, amount, reason, balanceAfter);
    }
}
