package com.gommit.domain.report.entity;

import com.gommit.global.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "user_penalties")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Penalty extends BaseEntity {

    public static final int MIN_SUSPENSION_DAYS = 1;
    public static final int MAX_SUSPENSION_DAYS = 365;

    @Column(nullable = false)
    private Long reportId;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PenaltyType penaltyType;

    private LocalDateTime endsAt;

    private Integer amount;

    private LocalDateTime revokedAt;

    private Penalty(Long reportId, Long userId, PenaltyType penaltyType, LocalDateTime endsAt, Integer amount) {
        this.reportId = reportId;
        this.userId = userId;
        this.penaltyType = penaltyType;
        this.endsAt = endsAt;
        this.amount = amount;
    }

    public static Penalty warning(Long reportId, Long userId) {
        return new Penalty(reportId, userId, PenaltyType.WARNING, null, null);
    }

    public static Penalty suspension(Long reportId, Long userId, int days) {
        return new Penalty(
                reportId, userId, PenaltyType.SUSPENSION, LocalDateTime.now().plusDays(days), null);
    }

    public static Penalty permanentBan(Long reportId, Long userId) {
        return new Penalty(reportId, userId, PenaltyType.PERMANENT_BAN, null, null);
    }

    public static Penalty pointForfeit(Long reportId, Long userId, int amount) {
        return new Penalty(reportId, userId, PenaltyType.POINT_FORFEIT, null, amount);
    }

    // 잔액이 모자라 요청보다 적게 깎였을 때 실제 값으로 맞춘다. 복구는 이 값을 봐야 한다.
    public void recordForfeited(int actualAmount) {
        this.amount = actualAmount;
    }

    public void revoke() {
        this.revokedAt = LocalDateTime.now();
    }

    public boolean isRevoked() {
        return this.revokedAt != null;
    }

    public boolean blocksLogin(LocalDateTime now) {
        if (isRevoked()) {
            return false;
        }
        if (this.penaltyType == PenaltyType.PERMANENT_BAN) {
            return true;
        }
        return this.penaltyType == PenaltyType.SUSPENSION && this.endsAt != null && this.endsAt.isAfter(now);
    }
}
