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

    @Column(length = 500)
    private String videoUrl;
}
