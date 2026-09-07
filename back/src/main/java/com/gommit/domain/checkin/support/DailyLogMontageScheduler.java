package com.gommit.domain.checkin.support;

import com.gommit.domain.checkin.entity.DailyLog;
import com.gommit.domain.checkin.repository.DailyLogRepository;
import com.gommit.domain.checkin.service.DailyLogMontageService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 몽타주 마감 배치. 영상 없는 지난 날(오늘 이전, 최근 sweep-lookback-days 일 창) row 를 훑어 몽타주를 만든다.
// 실행 시각은 application.yml의 app.dailylog.montage-sweep-cron 으로 조정. 인스턴스가 깨어 있는 시간대라야 한다(EC2 18:00 정지 창 회피).

// TODO(feat/20): businessDate 경계가 04:00 으로 바뀌면 "오늘" 계산을 공용 BusinessDate 컴포넌트로 교체하고
//   sweep cron 을 경계 뒤(예: 05:00)로 옮긴다. 지금은 sweep 의 LocalDate.now(00:00 경계)와 businessDate(00:00 경계)가
//   일치해 진행 중인 날이 대상에 안 들어오지만, 경계가 어긋나면 00:00~04:00 창에서 아직 인증받는 날을 몽타주할 수 있다.
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyLogMontageScheduler {

    private final DailyLogRepository dailyLogRepository;
    private final DailyLogMontageService montageService;
    private final Clock clock;

    @Value("${app.dailylog.montage-sweep-lookback-days:7}")
    private int sweepLookbackDays; // 마감 배치가 확인하는 최대 일수.

    // sweep 1회 처리 상한. ffmpeg 이 CPU 를 오래 물어 EC2 크레딧을 한 번에 소모하지 않도록 제한 —
    // 초과분은 다음 회차(부팅/예약)에서 처리된다.
    @Value("${app.dailylog.montage-sweep-max-per-run:20}")
    private int sweepMaxPerRun;

    @EventListener(ApplicationReadyEvent.class)
    public void sweepOnStartup() {
        sweepPendingMontages();
    }

    @Scheduled(cron = "${app.dailylog.montage-sweep-cron:0 0 4 * * *}", zone = "${app.time-zone:Asia/Seoul}")
    public void sweepPendingMontages() {
        // TODO(feat/20): 공용 BusinessDate 로 교체 + cron 을 경계 뒤로 (클래스 상단 주석 참고).
        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.minusDays(sweepLookbackDays);
        List<DailyLog> matched =
                dailyLogRepository.findByVideoKeyIsNullAndLogDateBetween(from, today.minusDays(1));
        if (matched.isEmpty()) {
            return;
        }
        List<DailyLog> pending =
                matched.size() > sweepMaxPerRun ? matched.subList(0, sweepMaxPerRun) : matched;
        log.info(
                "DailyLog 몽타주 마감 배치 시작 — 대상 {}건 (미완료 {}건)", pending.size(), matched.size());

        int failed = 0;
        for (DailyLog dailyLog : pending) {
            try {
                montageService.generateMontage(dailyLog.getChallengeId(), dailyLog.getLogDate());
            } catch (Exception e) {
                failed++;
                log.warn(
                        "DailyLog 몽타주 생성 실패 — 다음 실행에서 재시도 (challengeId={}, logDate={})",
                        dailyLog.getChallengeId(),
                        dailyLog.getLogDate(),
                        e);
            }
        }
        if (failed > 0) {
            log.warn("DailyLog 몽타주 마감 배치 종료 — 대상 {}건 중 {}건 실패", pending.size(), failed);
        }
    }
}
