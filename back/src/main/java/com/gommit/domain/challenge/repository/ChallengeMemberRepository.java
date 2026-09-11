package com.gommit.domain.challenge.repository;

import com.gommit.domain.challenge.entity.ChallengeMember;
import com.gommit.domain.challenge.entity.ChallengeMemberRole;
import com.gommit.domain.challenge.entity.ChallengeMemberStatus;
import com.gommit.domain.challenge.entity.ExtensionChoice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChallengeMemberRepository extends JpaRepository<ChallengeMember, Long> {
    List<ChallengeMember> findAllByChallengeId(Long challengeId);

    Optional<ChallengeMember> findByChallengeIdAndUserId(Long challengeId, Long userId);

    Optional<ChallengeMember> findByChallengeIdAndRole(Long challengeId, ChallengeMemberRole role);

    long countByChallengeIdAndStatus(Long challengeId, ChallengeMemberStatus status);

    long countByChallengeIdAndStatusAndExtensionChoice(
            Long challengeId, ChallengeMemberStatus status, ExtensionChoice extensionChoice);

    List<ChallengeMember> findAllByChallengeIdAndStatus(Long challengeId, ChallengeMemberStatus status);

    @EntityGraph(attributePaths = "challenge")
    List<ChallengeMember> findAllByUserIdAndStatus(Long userId, ChallengeMemberStatus status);

    List<ChallengeMember> findAllByChallengeIdAndStatusAndExtensionChoice(
            Long challengeId, ChallengeMemberStatus challengeMemberStatus, ExtensionChoice extensionChoice);

    // 월간 머지 아카이브(내가 속한 챌린지 목록)를 만들 때 쓴다.
    List<ChallengeMember> findAllByUserId(Long userId);

    // Record가 그룹 머지 결과를 "그룹 멤버에게만" 공개하려고 쓰는 멤버십 체크.
    @Query("select count(m) > 0 from ChallengeMember m "
            + "where m.challenge.id = :challengeId and m.userId = :userId and m.status = :status")
    boolean existsActiveMember(
            @Param("challengeId") Long challengeId,
            @Param("userId") Long userId,
            @Param("status") ChallengeMemberStatus status);
}
