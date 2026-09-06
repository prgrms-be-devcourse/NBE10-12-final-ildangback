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

    // 몽타주 폴백 배치 대상 — 영상 없는 지난 로그(오늘 이전) 전부. 서버 재시작/ffmpeg 일시 실패로 밀린 날을 훑는다.
    List<DailyLog> findByVideoKeyIsNullAndLogDateBefore(LocalDate date);

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
