package com.gommit.domain.checkin.support;

import com.gommit.domain.checkin.entity.DailyLog;
import com.gommit.domain.checkin.repository.DailyLogRepository;
import com.gommit.domain.checkin.service.DailyLogMontageService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 몽타주 생성 폴백 배치. 주경로(전원 완료 이벤트)를 놓친 날 — 서버 재시작, ffmpeg 일시 실패 등 —
// 을 훑어 재시도한다. 큐/아웃박스는 안 씀(단일 인스턴스, 폴백 배치로 충분).
// 다음날 인스턴스 가동 시간대(09:00 KST)로 잡는다 — 00:00 배치는 EC2 18:00 정지 창과 겹쳐 불가.
// 별도 빈(DailyLogMontageService) 호출 — 셀프 인보케이션이면 generateMontage() 의 @Transactional 이 걸리지 않는다.
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyLogMontageScheduler {

    private final DailyLogRepository dailyLogRepository;
    private final DailyLogMontageService montageService;
    private final Clock clock;

    @Scheduled(cron = "${app.dailylog.montage-fallback-cron:0 0 9 * * *}", zone = "${app.time-zone:Asia/Seoul}")
    public void runFallback() {
        LocalDate today = LocalDate.now(clock);
        List<DailyLog> pending = dailyLogRepository.findByVideoKeyIsNullAndLogDateBefore(today);
        if (pending.isEmpty()) {
            return;
        }
        log.info("DailyLog 몽타주 폴백 배치 시작 — 대상 {}건", pending.size());
        pending.forEach(dailyLog -> montageService.generateMontage(dailyLog.getChallengeId(), dailyLog.getLogDate()));
    }
}
