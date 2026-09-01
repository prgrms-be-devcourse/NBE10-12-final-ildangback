package com.gommit.domain.challenge.entity;

import com.gommit.global.base.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.AccessLevel;
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

    @Column(nullable = false)
    private boolean allowPhoto;
}
