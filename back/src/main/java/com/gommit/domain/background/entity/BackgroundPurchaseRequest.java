package com.gommit.domain.background.entity;

import com.gommit.global.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "background_purchase_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BackgroundPurchaseRequest extends BaseEntity {

    @Column(nullable = false)
    private Long groupId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "background_id", nullable = false)
    private Background background;

    @Column(nullable = false)
    private Long requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseRequestStatus status;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Builder
    public BackgroundPurchaseRequest(Long groupId, Background background, Long requestedBy, LocalDateTime expiresAt) {
        this.groupId = groupId;
        this.background = background;
        this.requestedBy = requestedBy;
        this.expiresAt = expiresAt;
        this.status = PurchaseRequestStatus.VOTING;
    }

    public boolean isVoting() {
        return this.status == PurchaseRequestStatus.VOTING;
    }

    public boolean isExpired(LocalDateTime now) {
        return now.isAfter(this.expiresAt);
    }

    public void approve() {
        this.status = PurchaseRequestStatus.APPROVED;
    }

    public void reject() {
        this.status = PurchaseRequestStatus.REJECTED;
    }
}
