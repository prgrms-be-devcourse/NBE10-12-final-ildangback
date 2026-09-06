package com.gommit.domain.checkin.service;

import com.gommit.domain.checkin.entity.CheckIn;
import com.gommit.domain.checkin.entity.DailyLog;
import com.gommit.domain.checkin.media.CheckInMediaStore;
import com.gommit.domain.checkin.media.DailyLogMediaStore;
import com.gommit.domain.checkin.repository.CheckInRepository;
import com.gommit.domain.checkin.repository.DailyLogRepository;
import com.gommit.domain.checkin.support.DailyLogMontageBuilder;
import com.gommit.domain.checkin.support.DailyLogMontageBuilder.Frame;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// DailyLog 몽타주(그 날 인증 사진 슬라이드쇼) 생성 실행 단위.
// @Transactional 이 실제로 걸리도록 항상 다른 빈(DailyLogMontageEventListener, DailyLogMontageScheduler)에서 호출한다.
// 이 클래스 안에서 generateMontage() 를 셀프 인보케이션하지 않는다 — 셀프 호출은 프록시를 우회해 트랜잭션이 걸리지 않는다.
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyLogMontageService {

    private final DailyLogRepository dailyLogRepository;
    private final CheckInRepository checkInRepository;
    private final CheckInMediaStore checkInMediaStore;
    private final DailyLogMediaStore dailyLogMediaStore;
    private final DailyLogMontageBuilder montageBuilder;

    @Transactional
    public void generateMontage(Long challengeId, LocalDate businessDate) {
        DailyLog dailyLog = dailyLogRepository
                .findByChallengeIdAndLogDate(challengeId, businessDate)
                .orElse(null);
        if (dailyLog == null || dailyLog.getVideoKey() != null) {
            return; // row 없음(레이스) 또는 이미 생성됨 — 재실행해도 안전(idempotent)
        }

        List<CheckIn> checkIns = checkInRepository.findByChallengeIdAndBusinessDate(challengeId, businessDate);
        if (checkIns.isEmpty()) {
            return;
        }

        List<Frame> frames = checkIns.stream()
                .map(c -> new Frame(checkInMediaStore.load(c.getMediaKey()), extensionOf(c.getMediaKey())))
                .toList();

        montageBuilder
                .build(frames)
                .ifPresentOrElse(
                        video -> dailyLog.attachVideo(dailyLogMediaStore.store(video)),
                        () -> log.warn(
                                "DailyLog 몽타주 생성 실패/ffmpeg 부재 — videoKey null 유지 (challengeId={}, businessDate={})",
                                challengeId,
                                businessDate));
    }

    private static String extensionOf(String storageKey) {
        int dot = storageKey.lastIndexOf('.');
        return (dot < 0 || dot == storageKey.length() - 1) ? "jpg" : storageKey.substring(dot + 1);
    }
}
