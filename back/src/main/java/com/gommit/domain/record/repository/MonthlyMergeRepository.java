package com.gommit.domain.record.repository;

import com.gommit.domain.record.entity.MonthlyMerge;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlyMergeRepository extends JpaRepository<MonthlyMerge, Long> {

    List<MonthlyMerge> findByChallengeIdOrderBySeqNoDesc(Long challengeId);

    Optional<MonthlyMerge> findByChallengeIdAndSeqNo(Long challengeId, int seqNo);

    // 배치가 다음 회차를 만들 때 직전 회차의 periodEnd를 이어받기 위해 쓴다.
    Optional<MonthlyMerge> findTopByChallengeIdOrderBySeqNoDesc(Long challengeId);

    // "월간 머지 N/M개" 진행률 표시용 - 지금까지 발행된 회차 수.
    int countByChallengeId(Long challengeId);

    // 챌린지 여러 개의 발행 회차 수를 한 번에 집계할 때 쓴다(N+1 방지) - 서비스에서
    // challengeId별로 그룹핑해서 센다.
    List<MonthlyMerge> findAllByChallengeIdIn(List<Long> challengeIds);

    // 배치 재실행 시 같은 회차를 중복 발행하지 않도록 막는 멱등성 체크.
    boolean existsByChallengeIdAndSeqNo(Long challengeId, int seqNo);
}
