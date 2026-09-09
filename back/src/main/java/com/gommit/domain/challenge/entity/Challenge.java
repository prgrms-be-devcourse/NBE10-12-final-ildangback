package com.gommit.domain.challenge.entity;

import com.gommit.domain.checkin.entity.CheckInType;
import com.gommit.global.base.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "challenges")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Challenge extends BaseEntity {

    @Column(nullable = false)
    private Long groupId;

    @Column(nullable = false)
    private int seqNo;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChallengeStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FrequencyType frequencyType;

    private Integer frequencyValue;

    @Column(length = 30)
    private String daysOfWeek;

    @Column(nullable = false)
    private int dailyCheckInCount;

    @Column(nullable = false)
    private int requiredDayCount;

    @Column(nullable = false)
    private int groupCurrentStreak;

    @Column(nullable = false)
    private int groupBestStreak;

    // ACTIVE 멤버 전원이 마지막으로 하루 인증 목표를 모두 채운 businessDate. 그룹 스트릭 연속성 판정에 쓴다.
    private LocalDate groupLastCompletedDate;

    @Column(nullable = false)
    private boolean allowPhoto;

    @Builder
    public Challenge(
            Long groupId,
            int seqNo,
            LocalDate startDate,
            LocalDate endDate,
            FrequencyType frequencyType,
            Integer frequencyValue,
            String daysOfWeek,
            int dailyCheckInCount,
            int requiredDayCount,
            int groupCurrentStreak,
            int groupBestStreak,
            boolean allowPhoto) {
        this.groupId = groupId;
        this.seqNo = seqNo;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = ChallengeStatus.READY;
        this.frequencyType = frequencyType;
        this.frequencyValue = frequencyValue;
        this.daysOfWeek = daysOfWeek;
        this.dailyCheckInCount = dailyCheckInCount;
        this.requiredDayCount = requiredDayCount;
        this.groupCurrentStreak = groupCurrentStreak;
        this.groupBestStreak = groupBestStreak;
        this.allowPhoto = allowPhoto;
    }

    public void updateSettings(
            LocalDate startDate,
            LocalDate endDate,
            FrequencyType frequencyType,
            Integer frequencyValue,
            String daysOfWeek,
            int dailyCheckInCount,
            int requiredDayCount,
            boolean allowPhoto) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.frequencyType = frequencyType;
        this.frequencyValue = frequencyValue;
        this.daysOfWeek = daysOfWeek;
        this.dailyCheckInCount = dailyCheckInCount;
        this.requiredDayCount = requiredDayCount;
        this.allowPhoto = allowPhoto;
    }

    // 이 챌린지가 허용하는 인증 방식 목록. 현재는 사진 허용 여부만 저장한다.
    public List<CheckInType> allowedCheckInTypes() {
        return allowPhoto ? List.of(CheckInType.PHOTO) : List.of();
    }

    public void activate() {
        this.status = ChallengeStatus.ACTIVE;
    }

    public void end() {
        this.status = ChallengeStatus.ENDED;
    }

    // 이번 인증으로 ACTIVE 멤버 전원이 businessDate 의 목표를 채웠을 때 호출한다.
    // previousCheckInDay 에도 그룹 전원이 채웠으면 연속으로 보고 +1, 아니면 1 로 리셋한다.
    public void completeGroupDay(LocalDate businessDate, LocalDate previousCheckInDay) {
        if (businessDate.equals(this.groupLastCompletedDate)) {
            return;
        }
        boolean consecutive = previousCheckInDay != null && previousCheckInDay.equals(this.groupLastCompletedDate);
        this.groupCurrentStreak = consecutive ? this.groupCurrentStreak + 1 : 1;
        this.groupBestStreak = Math.max(this.groupBestStreak, this.groupCurrentStreak);
        this.groupLastCompletedDate = businessDate;
    }
}
