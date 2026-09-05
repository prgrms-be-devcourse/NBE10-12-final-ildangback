package com.gommit.domain.user.entity;

import com.gommit.global.base.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "email_tokens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 255)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailTokenType tokenType;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime usedAt;

    public EmailToken(User user, String tokenHash, EmailTokenType tokenType, LocalDateTime expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.tokenType = tokenType;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }

    public boolean isUsed() {
        return usedAt != null;
    }
}
