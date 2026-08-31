package com.gommit.domain.challenge.repository;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChallengeRepository extends JpaRepository<Challenge, Long> {
    Optional<Challenge> findFirstByGroupIdAndStatus(Long groupId, ChallengeStatus challengeStatus);
}
