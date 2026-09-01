package com.gommit.domain.challenge.entity;

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
@Table(name = "challenge_members")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChallengeMember extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "challenge_id", nullable = false)
    private Challenge challenge;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChallengeMemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChallengeMemberStatus status;

    @Column(nullable = false)
    private int currentStreak;

    @Column(nullable = false)
    private int bestStreak;

    private LocalDateTime leftAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExtensionChoice extensionChoice;

    @Builder
    public ChallengeMember(Challenge challenge, Long userId, ChallengeMemberRole role) {
        this.challenge = challenge;
        this.userId = userId;
        this.role = role;
        this.status = ChallengeMemberStatus.ACTIVE;
        this.currentStreak = 0;
        this.bestStreak = 0;
        this.leftAt = null;
        this.extensionChoice = ExtensionChoice.PENDING;
    }
}
