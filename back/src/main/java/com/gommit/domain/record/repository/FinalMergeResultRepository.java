package com.gommit.domain.record.repository;

import com.gommit.domain.record.entity.FinalMergeResult;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinalMergeResultRepository extends JpaRepository<FinalMergeResult, Long> {

    List<FinalMergeResult> findByFinalMergeIdOrderByRanking(Long finalMergeId);

    Optional<FinalMergeResult> findByFinalMergeIdAndUserId(Long finalMergeId, Long userId);
}
