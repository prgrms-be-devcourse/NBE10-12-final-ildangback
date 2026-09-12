package com.gommit.domain.checkin.entity;

import com.gommit.global.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "daily_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyLog extends BaseEntity {

    @Column(nullable = false)
    private Long challengeId;

    @Column(nullable = false)
    private LocalDate logDate;

    @Column(length = 255)
    private String videoKey;

    private DailyLog(Long challengeId, LocalDate logDate) {
        this.challengeId = challengeId;
        this.logDate = logDate;
    }

    // 해당 challenge-day의 첫 인증 시점에 생성. 몽타주 생성 혹은 저장 실패로 DailyLog가 누락되지 않도록 미리 생성.
    public static DailyLog create(Long challengeId, LocalDate logDate) {
        return new DailyLog(challengeId, logDate);
    }

    // 몽타주(ffmpeg) 생성 완료 후 호출. 생성 실패시에는 호출되지 않아 videoKey가 null 로 남는다.
    public void attachVideo(String videoKey) {
        this.videoKey = videoKey;
    }
}
