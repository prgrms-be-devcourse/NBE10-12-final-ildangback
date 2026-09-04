package com.gommit.domain.challenge.repository;

import com.gommit.domain.challenge.entity.*;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChallengeRepository extends JpaRepository<Challenge, Long> {
    Optional<Challenge> findFirstByGroupIdAndStatus(Long groupId, ChallengeStatus challengeStatus);

    List<Challenge> findAllByGroupIdInAndStatus(List<Long> groupIds, ChallengeStatus challengeStatus);

    Optional<Challenge> findByGroupIdAndSeqNo(Long groupId, int seqNo);

    List<Challenge> findAllByStatus(ChallengeStatus status);
}
