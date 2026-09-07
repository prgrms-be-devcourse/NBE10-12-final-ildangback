package com.gommit.domain.record.repository;

import com.gommit.domain.record.entity.FinalMerge;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinalMergeRepository extends JpaRepository<FinalMerge, Long> {

    Optional<FinalMerge> findByChallengeId(Long challengeId);

    boolean existsByChallengeId(Long challengeId);

    // 챌린지 여러 개 중 최종 머지가 이미 발행된 것만 한 번에 조회할 때 쓴다(N+1 방지).
    List<FinalMerge> findAllByChallengeIdIn(List<Long> challengeIds);
}
