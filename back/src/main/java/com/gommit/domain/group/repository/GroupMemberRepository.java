package com.gommit.domain.group.repository;

import com.gommit.domain.group.entity.ChallengeGroup;
import com.gommit.domain.group.entity.GroupMember;
import com.gommit.domain.group.entity.GroupMemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    List<GroupMember> findAllByGroupIdAndStatus(Long groupId, GroupMemberStatus groupMemberStatus);

    @Query("""
        SELECT gm.group.id as groupId,
               COUNT(gm.id) as count
          FROM GroupMember gm
         WHERE gm.group.id IN :groupIds
           AND gm.status = :status
        GROUP BY gm.group.id
    """)
    List<GroupMemberCount> countByGroupIdsAndStatus(@Param("groupIds") List<Long> groupIds, @Param("status") GroupMemberStatus status);

    List<Long> group(ChallengeGroup group);
}
