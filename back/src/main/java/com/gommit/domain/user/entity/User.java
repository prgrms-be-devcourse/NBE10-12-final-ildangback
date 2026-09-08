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

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(length = 255)
    private String introduction;

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
        this.email = email;
        this.password = encodedPassword;
        this.nickname = nickname;
        this.role = UserRole.USER;
        this.personalStreak = 0;
        this.bestStreak = 0;
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

    public void updateStreak(int personalStreak, LocalDate lastCheckedInDate) {
        this.personalStreak = personalStreak;
        this.bestStreak = Math.max(this.bestStreak, personalStreak);
        this.lastCheckedInDate = lastCheckedInDate;
    }

    public void resetStreak() {
        this.personalStreak = 0;
    }

    // 소속된 챌린지 중 하나라도 businessDate 의 하루 인증 목표를 채웠을 때 호출한다(당일 1회).
    // 연속성은 "인증 대상일" 기준: previousCheckInDay 는 이 완료를 트리거한 챌린지의 직전 대상일.
    // 여러 챌린지 교차 스트릭이라 특정 챌린지 대상일에 정확히 일치할 필요는 없고, 직전 대상일 이후로
    // 활동이 이어졌으면(마지막 완료일 >= 직전 대상일) 연속으로 본다. 아니면 1 로 리셋.
    public void recordDailyCompletion(LocalDate businessDate, LocalDate previousCheckInDay) {
        if (businessDate.equals(this.lastCheckedInDate)) {
            return;
        }
        boolean consecutive = this.lastCheckedInDate != null
                && previousCheckInDay != null
                && !this.lastCheckedInDate.isBefore(previousCheckInDay);
        this.personalStreak = consecutive ? this.personalStreak + 1 : 1;
        this.bestStreak = Math.max(this.bestStreak, this.personalStreak);
        this.lastCheckedInDate = businessDate;
    }
}
