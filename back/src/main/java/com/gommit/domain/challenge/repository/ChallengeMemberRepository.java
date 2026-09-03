package com.gommit.domain.challenge.repository;

import com.gommit.domain.challenge.entity.ChallengeMember;
import com.gommit.domain.challenge.entity.ChallengeMemberRole;
import com.gommit.domain.challenge.entity.ChallengeMemberStatus;
import com.gommit.domain.challenge.entity.ExtensionChoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChallengeMemberRepository extends JpaRepository<ChallengeMember, Long> {
    Optional<ChallengeMember> findByChallengeIdAndUserId(Long challengeId, Long userId);

    Optional<ChallengeMember> findByChallengeIdAndRole(Long challengeId, ChallengeMemberRole role);

    long countByChallengeIdAndStatus(Long challengeId, ChallengeMemberStatus status);
    long countByChallengeIdAndStatusAndExtensionChoice(Long challengeId, ChallengeMemberStatus status, ExtensionChoice extensionChoice);

    List<ChallengeMember> findAllByChallengeIdAndStatus(Long challengeId, ChallengeMemberStatus status);

    List<ChallengeMember> findAllByUserIdAndStatus(Long userId, ChallengeMemberStatus status);
}
