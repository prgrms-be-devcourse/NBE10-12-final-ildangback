package com.gommit.domain.background.entity;

import com.gommit.global.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "background_purchase_votes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BackgroundPurchaseVote extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private BackgroundPurchaseRequest request;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private boolean agreed;

    @Builder
    public BackgroundPurchaseVote(BackgroundPurchaseRequest request, Long userId, boolean agreed) {
        this.request = request;
        this.userId = userId;
        this.agreed = agreed;
    }
}
