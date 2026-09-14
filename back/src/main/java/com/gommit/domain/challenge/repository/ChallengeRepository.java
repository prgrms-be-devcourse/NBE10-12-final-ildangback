package com.gommit.domain.challenge.repository;

import com.gommit.domain.challenge.entity.*;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ChallengeRepository extends JpaRepository<Challenge, Long> {
    Optional<Challenge> findFirstByGroupIdAndStatus(Long groupId, ChallengeStatus challengeStatus);

    List<Challenge> findAllByGroupIdInAndStatus(List<Long> groupIds, ChallengeStatus challengeStatus);

    Optional<Challenge> findByGroupIdAndSeqNo(Long groupId, int seqNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select c from Challenge c where c.status = com.gommit.domain.challenge.entity.ChallengeStatus.ACTIVE order by c.id")
    List<Challenge> findActiveForReminder();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select c from Challenge c where c.status = com.gommit.domain.challenge.entity.ChallengeStatus.READY order by c.id")
    List<Challenge> findReadyForActivation();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select c from Challenge c where c.status = com.gommit.domain.challenge.entity.ChallengeStatus.ACTIVE order by c.id")
    List<Challenge> findActiveForEnding();

    List<Challenge> findAllByStatus(ChallengeStatus status);

    List<Challenge> findAllByGroupIdOrderBySeqNoAsc(Long groupId);
}
