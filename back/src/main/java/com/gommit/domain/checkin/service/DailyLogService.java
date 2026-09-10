package com.gommit.domain.checkin.service;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeMemberStatus;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.checkin.dto.response.DailyLogResponse;
import com.gommit.domain.checkin.entity.DailyLog;
import com.gommit.domain.checkin.event.DailyLogCompletedEvent;
import com.gommit.domain.checkin.media.DailyLogMediaStore;
import com.gommit.domain.checkin.repository.CheckInRepository;
import com.gommit.domain.checkin.repository.DailyLogRepository;
import com.gommit.domain.checkin.support.CheckInPreconditions;
import com.gommit.domain.checkin.support.CheckInPreconditions.ReadDateAccess;
import com.gommit.global.dto.SliceResponse;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DailyLogService {

    private final DailyLogRepository dailyLogRepository;
    private final CheckInRepository checkInRepository;
    private final ChallengeMemberRepository challengeMemberRepository;
    private final CheckInPreconditions preconditions;
    private final DailyLogMediaStore mediaStore;
    private final ApplicationEventPublisher eventPublisher;

    // CheckInService.submit() 이 인증을 저장한 같은 트랜잭션에서 호출한다.
    // 그 challenge-day 첫 인증이면 row 를 만들고, 전원 완료 시점이면 몽타주 생성 이벤트를 발행한다(커밋 후 비동기 처리).
    @Transactional
    public void recordCheckIn(Challenge challenge, LocalDate businessDate) {
        ensureLogRow(challenge.getId(), businessDate);

        Counts counts = countsFor(challenge, businessDate);
        if (counts.total() > 0 && counts.completed() == counts.total()) {
            eventPublisher.publishEvent(new DailyLogCompletedEvent(challenge.getId(), businessDate));
        }
    }

    // 일일로그 목록 조회 (무한스크롤) — 활동 있던 날(row 존재)만 나열.
    public SliceResponse<DailyLogResponse> getDailyLogs(Long userId, Long challengeId, Long cursor, int size) {
        ReadDateAccess access = preconditions.resolveReadDateAccess(challengeId, userId);

        List<DailyLog> rows =
                dailyLogRepository.findLogs(challengeId, access.maxBusinessDate(), cursor, PageRequest.of(0, size + 1));

        SliceResponse<DailyLog> page = SliceResponse.ofCursor(rows, size, DailyLog::getId);
        List<DailyLogResponse> content = page.content().stream()
                .map(row -> toResponse(row, access.challenge()))
                .toList();

        return new SliceResponse<>(content, page.hasNext(), page.nextCursor());
    }

    // 일일로그 단건 조회 — row 없으면 무조건 404(휴무일/무기록을 구분하지 않는다).
    public DailyLogResponse getDailyLog(Long userId, Long challengeId, LocalDate date) {
        ReadDateAccess access = preconditions.resolveReadDateAccess(challengeId, userId);
        if (!access.allows(date)) {
            throw new BusinessException(ErrorCode.CHALLENGE_NOT_MEMBER);
        }

        DailyLog dailyLog = dailyLogRepository
                .findByChallengeIdAndLogDate(challengeId, date)
                .orElseThrow(() -> new BusinessException(ErrorCode.DAILY_LOG_NOT_FOUND));

        return toResponse(dailyLog, access.challenge());
    }

    public Resource loadMedia(Long userId, Long dailyLogId) {
        DailyLog dailyLog = dailyLogRepository
                .findById(dailyLogId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DAILY_LOG_NOT_FOUND));
        ReadDateAccess access = preconditions.resolveReadDateAccess(dailyLog.getChallengeId(), userId);
        if (!access.allows(dailyLog.getLogDate())) {
            throw new BusinessException(ErrorCode.CHALLENGE_NOT_MEMBER);
        }
        if (dailyLog.getVideoKey() == null) {
            throw new BusinessException(ErrorCode.MEDIA_NOT_FOUND);
        }
        return mediaStore.load(dailyLog.getVideoKey());
    }

    private void ensureLogRow(Long challengeId, LocalDate businessDate) {
        if (dailyLogRepository.existsByChallengeIdAndLogDate(challengeId, businessDate)) {
            return;
        }
        try {
            dailyLogRepository.saveAndFlush(DailyLog.create(challengeId, businessDate));
        } catch (DataIntegrityViolationException e) {
            // uk_daily_logs 위반 = 동시 요청이 같은 challenge-day row 를 먼저 만듦 — 정상 상황, 무시.
            log.debug("DailyLog row 동시 생성 경합 — 무시 (challengeId={}, businessDate={})", challengeId, businessDate);
        }
    }

    private DailyLogResponse toResponse(DailyLog dailyLog, Challenge challenge) {
        Counts counts = countsFor(challenge, dailyLog.getLogDate());
        String videoUrl = dailyLog.getVideoKey() == null ? null : mediaUrl(dailyLog.getId());
        return new DailyLogResponse(
                dailyLog.getId(), dailyLog.getLogDate(), videoUrl, counts.completed(), counts.total());
    }

    // 일일로그 몽타주 영상 조회용 상대경로. 프론트에서 origin(예: https://go-mmit.site)을 붙인다.
    private static String mediaUrl(Long dailyLogId) {
        return "/api/daily-logs/" + dailyLogId + "/media";
    }

    // completedCount: 그 businessDate 에 challenge.dailyCheckInCount 이상 인증한 멤버 수.
    // totalCount: 그 businessDate 시점 ACTIVE 였던 멤버 수(스냅샷). 컬럼으로 저장하지 않고 조회 시 계산한다.
    private Counts countsFor(Challenge challenge, LocalDate businessDate) {
        int completed = checkInRepository
                .findCompletedUserIds(challenge.getId(), businessDate, challenge.getDailyCheckInCount())
                .size();

        // TODO(feat/20): businessDate 경계가 04:00 으로 바뀌면 이 벽시계 창도 04:00~다음날 04:00 으로 옮긴다
        LocalDateTime startOfDay = businessDate.atStartOfDay();
        LocalDateTime endOfDay = businessDate.plusDays(1).atStartOfDay();
        long total = challengeMemberRepository.countSnapshotMembers(
                challenge.getId(), startOfDay, endOfDay, ChallengeMemberStatus.ACTIVE);

        return new Counts(completed, (int) total);
    }

    private record Counts(int completed, int total) {}
}
