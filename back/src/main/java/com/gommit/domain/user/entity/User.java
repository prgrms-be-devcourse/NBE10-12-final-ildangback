package com.gommit.domain.user.entity;

import com.gommit.global.base.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    private static final String DELETED_EMAIL_FORMAT = "deleted_%d@example.com";
    private static final String DELETED_PASSWORD = "(deleted)";
    private static final String DELETED_NICKNAME_PREFIX = "탈퇴한사용자_";

    @Column(nullable = false, length = 255)
    private String email;

    @Column(length = 255)
    private String password;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(length = 255)
    private String introduction;

    @Column(nullable = false)
    private boolean emailVerified;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(nullable = false)
    private int personalStreak;

    @Column(nullable = false)
    private int bestStreak;

    private LocalDate lastCheckedInDate;

    private LocalDateTime deletedAt;

    public User(String email, String encodedPassword, String nickname) {
        this(email, nickname);
        this.password = encodedPassword;
    }

    public User(String email, String nickname) {
        this.email = email;
        this.nickname = nickname;
        this.role = UserRole.USER;
        this.personalStreak = 0;
        this.bestStreak = 0;
    }

    public void verifyEmail() {
        this.emailVerified = true;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void updateIntroduction(String introduction) {
        this.introduction = introduction;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void deleteAccount() {
        this.email = DELETED_EMAIL_FORMAT.formatted(getId());
        this.nickname = DELETED_NICKNAME_PREFIX + getId();
        this.password = DELETED_PASSWORD;
        this.introduction = null;
        this.deletedAt = LocalDateTime.now();
    }

    public void updateStreak(LocalDate businessDate, LocalDate lastRequiredCheckInDay) {
        if (businessDate.equals(this.lastCheckedInDate)) {
            return;
        }
        this.personalStreak = isUnbroken(lastRequiredCheckInDay) ? this.personalStreak + 1 : 1;
        this.bestStreak = Math.max(this.bestStreak, this.personalStreak);
        this.lastCheckedInDate = businessDate;
    }

    public int calculateStreak(LocalDate businessDate, LocalDate lastRequiredCheckInDay) {
        if (this.lastCheckedInDate == null) {
            return 0;
        }
        if (this.lastCheckedInDate.equals(businessDate)) {
            return this.personalStreak;
        }
        return isUnbroken(lastRequiredCheckInDay) ? this.personalStreak : 0;
    }

    private boolean isUnbroken(LocalDate lastRequiredCheckInDay) {
        return this.lastCheckedInDate != null
                && (lastRequiredCheckInDay == null || !this.lastCheckedInDate.isBefore(lastRequiredCheckInDay));
    }
}
