package com.gommit.domain.challenge.repository;

import com.gommit.domain.challenge.entity.ChallengeMember;
import com.gommit.domain.challenge.entity.ChallengeMemberRole;
import com.gommit.domain.challenge.entity.ChallengeMemberStatus;
import com.gommit.domain.challenge.entity.ExtensionChoice;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChallengeMemberRepository extends JpaRepository<ChallengeMember, Long> {
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

    // DailyLog totalCount 스냅샷 — 그 businessDate 시점 ACTIVE 였던 멤버 수.
    @Query("""
            select count(m) from ChallengeMember m
            where m.challenge.id = :challengeId
              and m.createdAt < :endOfDay
              and (m.status = :active or m.leftAt >= :startOfDay)
            """)
    long countSnapshotMembers(
            @Param("challengeId") Long challengeId,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay,
            @Param("active") ChallengeMemberStatus active);

    // DailyLog 몽타주 그리드 칸의 멤버 순서(고정, 가입순=id오름차순) — 칸 배정에 userId만 쓰므로 프로젝션으로 반환
    @Query("""
            select m.userId from ChallengeMember m
            where m.challenge.id = :challengeId
              and m.createdAt < :endOfDay
              and (m.status = :active or m.leftAt >= :startOfDay)
            order by m.id asc
            """)
    List<Long> findSnapshotMemberUserIds(
            @Param("challengeId") Long challengeId,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay,
            @Param("active") ChallengeMemberStatus active);
}
