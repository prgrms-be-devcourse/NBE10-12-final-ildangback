package com.gommit.domain.checkin.entity;

import com.gommit.global.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "check_ins")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CheckIn extends BaseEntity {

    @Column(nullable = false)
    private Long challengeId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private int roundNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CheckInType checkInType;

    @Column(nullable = false, length = 255)
    private String mediaKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MediaType mediaType;

    @Column(length = 100)
    private String memo;

    @Column(nullable = false)
    private LocalDate businessDate;

    private CheckIn(
            Long challengeId,
            Long userId,
            int roundNo,
            CheckInType checkInType,
            String mediaKey,
            MediaType mediaType,
            String memo,
            LocalDate businessDate) {
        this.challengeId = challengeId;
        this.userId = userId;
        this.roundNo = roundNo;
        this.checkInType = checkInType;
        this.mediaKey = mediaKey;
        this.mediaType = mediaType;
        this.memo = memo;
        this.businessDate = businessDate;
    }

    public static CheckIn create(
            Long challengeId,
            Long userId,
            int roundNo,
            CheckInType checkInType,
            String mediaKey,
            MediaType mediaType,
            String memo,
            LocalDate businessDate) {
        return new CheckIn(challengeId, userId, roundNo, checkInType, mediaKey, mediaType, memo, businessDate);
    }
}
