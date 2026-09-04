package com.gommit.domain.group.repository;

import com.gommit.domain.group.entity.GroupMember;
import com.gommit.domain.group.entity.GroupMemberStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    List<GroupMember> findAllByGroupIdAndStatus(Long groupId, GroupMemberStatus status);

    Optional<GroupMember> findByGroupIdAndUserId(Long groupId, Long userId);

    boolean existsByGroupIdAndUserId(Long groupId, Long userId);

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
}
