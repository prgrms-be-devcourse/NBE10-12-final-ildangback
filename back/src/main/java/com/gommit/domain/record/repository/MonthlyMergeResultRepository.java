package com.gommit.domain.record.repository;

import com.gommit.domain.record.entity.MonthlyMergeResult;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MonthlyMergeResultRepository extends JpaRepository<MonthlyMergeResult, Long> {

    List<MonthlyMergeResult> findByMonthlyMergeIdOrderByRanking(Long monthlyMergeId);

    Optional<MonthlyMergeResult> findByMonthlyMergeIdAndUserId(Long monthlyMergeId, Long userId);

    // 개인 전체 통계(GET /users/me/stats) 집계용 - 내가 참여한 모든 월간 머지 결과.
    List<MonthlyMergeResult> findAllByUserId(Long userId);

    // 내 월간 머지 아카이브(프로필) 커서 조회 - UserPointHistoryRepository.findHistories와
    // 동일한 방식(id desc + id < cursor)이다.
    @Query("select r from MonthlyMergeResult r "
            + "where r.userId = :userId "
            + "and (:cursor is null or r.id < :cursor) "
            + "order by r.id desc")
    List<MonthlyMergeResult> findHistoriesByUserId(
            @Param("userId") Long userId, @Param("cursor") Long cursor, Pageable pageable);
}
