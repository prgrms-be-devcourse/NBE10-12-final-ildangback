package com.gommit.domain.checkin.repository;

import com.gommit.domain.checkin.entity.DailyLog;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyLogRepository extends JpaRepository<DailyLog, Long> {

    // 그 challenge-day 의 bookkeeping row 확보. 동시 첫 인증이 경합해도 uk_daily_logs 충돌을
    // DB 에서 no-op 로 흡수한다(ON DUPLICATE KEY UPDATE id=id). 네이티브라 영속성 컨텍스트를
    // 거치지 않아 실패한 flush 로 세션이 오염되지 않는다. created_at/updated_at 은 auditing 우회라 직접 채운다.
    @Modifying(flushAutomatically = true)
    @Query(value = """
                    insert into daily_logs (challenge_id, log_date, created_at, updated_at)
                    values (:challengeId, :logDate, now(6), now(6))
                    on duplicate key update id = id
                    """, nativeQuery = true)
    void insertLogRowIfAbsent(@Param("challengeId") Long challengeId, @Param("logDate") LocalDate logDate);

    Optional<DailyLog> findByChallengeIdAndLogDate(Long challengeId, LocalDate logDate);

    // 몽타주 마감 배치 대상 — 영상 없는 지난 로그. 생성이 매번 실패하는 경우 대비를 위해 시간 범위 지정.
    List<DailyLog> findByVideoKeyIsNullAndLogDateBetween(LocalDate from, LocalDate to);

    // 목록 조회 — 활동 있던 날(row 존재)만 id 커서(내림차순). from~to(월 범위) 필터.
    // maxBusinessDate: 이탈 멤버 상한(null = 제한 없음).
    @Query("""
            select d from DailyLog d
            where d.challengeId = :challengeId
              and (:from is null or d.logDate >= :from)
              and (:to is null or d.logDate <= :to)
              and (:maxBusinessDate is null or d.logDate <= :maxBusinessDate)
              and (:cursorId is null or d.id < :cursorId)
            order by d.id desc
            """)
    List<DailyLog> findLogs(
            @Param("challengeId") Long challengeId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("maxBusinessDate") LocalDate maxBusinessDate,
            @Param("cursorId") Long cursorId,
            Pageable pageable);
}
