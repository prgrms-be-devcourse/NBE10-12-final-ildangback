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
import java.time.LocalDate;
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

    // 개인이 마지막으로 하루 인증 목표를 채운 시점 기준의 연속 완료일 수.
    // 완료 인증 시점에만 갱신되는 캐시라, 조회 시점 기준값은 lastCompletedDate 와 함께 판단한다.
    @Column(nullable = false)
    private int currentStreak;

    // currentStreak 이 도달한 최고값. 한 번 오르면 내려가지 않는다.
    @Column(nullable = false)
    private int bestStreak;

    // 개인이 마지막으로 하루 인증 목표를 모두 채운 businessDate. 스트릭 연속성 판정에 쓴다.
    private LocalDate lastCompletedDate;

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

    public void changeRole(ChallengeMemberRole role) {
        this.role = role;
    }

    public void changeExtensionChoice(ExtensionChoice choice) {
        this.extensionChoice = choice;
    }

    public void leave() {
        this.status = ChallengeMemberStatus.LEFT;
        this.leftAt = LocalDateTime.now();
    }

    public void kick() {
        this.status = ChallengeMemberStatus.KICKED;
        this.leftAt = LocalDateTime.now();
    }

    // 개인이 businessDate 의 하루 인증 목표를 모두 채웠을 때 호출한다.
    // previousCheckInDay(직전 인증 대상일)에도 완료했으면 연속으로 보고 +1, 아니면 1 로 리셋한다.
    public void completeDay(LocalDate businessDate, LocalDate previousCheckInDay) {
        if (businessDate.equals(this.lastCompletedDate)) {
            return;
        }
        boolean consecutive = previousCheckInDay != null && previousCheckInDay.equals(this.lastCompletedDate);
        this.currentStreak = consecutive ? this.currentStreak + 1 : 1;
        this.bestStreak = Math.max(this.bestStreak, this.currentStreak);
        this.lastCompletedDate = businessDate;
    }
}
