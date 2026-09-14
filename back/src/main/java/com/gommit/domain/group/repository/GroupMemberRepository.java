package com.gommit.domain.group.repository;

import com.gommit.domain.group.entity.GroupMember;
import com.gommit.domain.group.entity.GroupMemberStatus;
import com.gommit.domain.group.entity.KickVoteChoice;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    List<GroupMember> findAllByGroupIdAndStatus(Long groupId, GroupMemberStatus status);

    @EntityGraph(attributePaths = "group")
    List<GroupMember> findAllByUserIdAndStatus(Long userId, GroupMemberStatus status);

    Optional<GroupMember> findByGroupIdAndUserId(Long groupId, Long userId);

    boolean existsByGroupIdAndUserId(Long groupId, Long userId);

    boolean existsByGroupIdAndUserIdAndStatus(Long groupId, Long userId, GroupMemberStatus status);

    long countByGroupIdAndStatus(Long groupId, GroupMemberStatus status);

    @Query("""
        SELECT gm.group.id as groupId,
               COUNT(gm.id) as count
          FROM GroupMember gm
         WHERE gm.group.id IN :groupIds
           AND gm.status = :status
        GROUP BY gm.group.id
    """)
    List<GroupMemberCount> countByGroupIdsAndStatus(
            @Param("groupIds") List<Long> groupIds, @Param("status") GroupMemberStatus status);

    long countByGroupIdAndStatusAndKickVoteChoice(
            Long groupId, GroupMemberStatus status, KickVoteChoice kickVoteChoice);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE GroupMember gm SET gm.kickVoteChoice = :none
        WHERE gm.group.id = :groupId AND gm.status = 'ACTIVE'
    """)
    void resetAllKickVoteChoices(@Param("groupId") Long groupId, @Param("none") KickVoteChoice none);

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE GroupMember gm
        SET gm.kickVoteChoice = 'NONE'
        WHERE gm.status = 'ACTIVE'
            AND gm.group.id IN(
                    SELECT g.id FROM ChallengeGroup g
                    WHERE g.kickVoteStartedAt IS NOT NULL
                        AND g.kickVoteStartedAt < :expiredBefore
            )
    """)
    int bulkResetExpiredKickVoteChoice(@Param("expiredBefore") LocalDateTime expiredBefore);
}
