package com.gommit.domain.challenge.repository;

import com.gommit.domain.challenge.entity.ChallengeMember;
import com.gommit.domain.challenge.entity.ChallengeMemberRole;
import com.gommit.domain.challenge.entity.ChallengeMemberStatus;
import com.gommit.domain.challenge.entity.ExtensionChoice;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChallengeMemberRepository extends JpaRepository<ChallengeMember, Long> {
    Optional<ChallengeMember> findByChallengeIdAndUserId(Long challengeId, Long userId);

    Optional<ChallengeMember> findByChallengeIdAndRole(Long challengeId, ChallengeMemberRole role);

    long countByChallengeIdAndStatus(Long challengeId, ChallengeMemberStatus status);

    long countByChallengeIdAndStatusAndExtensionChoice(
            Long challengeId, ChallengeMemberStatus status, ExtensionChoice extensionChoice);

    List<ChallengeMember> findAllByChallengeIdAndStatus(Long challengeId, ChallengeMemberStatus status);

    // 그룹 하루 완료 판정용. 잠금 읽기라 스냅샷(REPEATABLE READ)을 우회해 다른 트랜잭션이 커밋한 최신 완료 상태를 본다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from ChallengeMember m where m.challenge.id = :challengeId and m.status = :status")
    List<ChallengeMember> findAllForUpdateByChallengeIdAndStatus(
            @Param("challengeId") Long challengeId, @Param("status") ChallengeMemberStatus status);

    @EntityGraph(attributePaths = "challenge")
    List<ChallengeMember> findAllByUserIdAndStatus(Long userId, ChallengeMemberStatus status);

    List<ChallengeMember> findAllByChallengeIdAndStatusAndExtensionChoice(
            Long challengeId, ChallengeMemberStatus challengeMemberStatus, ExtensionChoice extensionChoice);
}
