package com.gommit.domain.checkin.repository;

import com.gommit.domain.checkin.entity.DailyLog;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyLogRepository extends JpaRepository<DailyLog, Long> {

    boolean existsByChallengeIdAndLogDate(Long challengeId, LocalDate logDate);

    Optional<DailyLog> findByChallengeIdAndLogDate(Long challengeId, LocalDate logDate);

    // 몽타주 마감 배치 대상 — 영상 없는 지난 로그. 생성이 매번 실패하는 경우 대비를 위해 시간 범위 지정.
    List<DailyLog> findByVideoKeyIsNullAndLogDateBetween(LocalDate from, LocalDate to);

    // 목록 조회 — 활동 있던 날(row 존재)만 id 커서(내림차순). maxBusinessDate: 이탈 멤버 상한(null = 제한 없음).
    @Query("""
            select d from DailyLog d
            where d.challengeId = :challengeId
              and (:maxBusinessDate is null or d.logDate <= :maxBusinessDate)
              and (:cursorId is null or d.id < :cursorId)
            order by d.id desc
            """)
    List<DailyLog> findLogs(
            @Param("challengeId") Long challengeId,
            @Param("maxBusinessDate") LocalDate maxBusinessDate,
            @Param("cursorId") Long cursorId,
            Pageable pageable);
}
