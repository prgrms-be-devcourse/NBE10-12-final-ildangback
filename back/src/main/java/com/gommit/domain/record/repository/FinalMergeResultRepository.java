package com.gommit.domain.record.repository;

import com.gommit.domain.record.entity.FinalMergeResult;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinalMergeResultRepository extends JpaRepository<FinalMergeResult, Long> {

    List<FinalMergeResult> findByFinalMergeIdOrderByRanking(Long finalMergeId);

    Optional<FinalMergeResult> findByFinalMergeIdAndUserId(Long finalMergeId, Long userId);

    // 개인 전체 통계(GET /users/me/stats) 집계용 - 내가 참여한 모든 최종 머지 결과.
    List<FinalMergeResult> findAllByUserId(Long userId);
}
