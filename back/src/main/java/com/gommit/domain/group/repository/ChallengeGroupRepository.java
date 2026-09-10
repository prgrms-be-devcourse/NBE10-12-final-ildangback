package com.gommit.domain.group.repository;

import com.gommit.domain.group.entity.ChallengeGroup;
import com.gommit.domain.group.entity.GroupStatus;
import com.gommit.domain.group.entity.Visibility;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChallengeGroupRepository extends JpaRepository<ChallengeGroup, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from ChallengeGroup g where g.id = :groupId")
    Optional<ChallengeGroup> findByIdWithLock(@Param("groupId") Long groupId);

    List<ChallengeGroup> findAllByVisibilityAndStatus(Visibility visibility, GroupStatus status);

    @Query("select g.name from ChallengeGroup g where g.id = :groupId")
    Optional<String> findNameById(@Param("groupId") Long groupId);

    boolean existsByInviteCode(String inviteCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select g
        from ChallengeGroup g
        where g.inviteCode = :inviteCode
    """)
    Optional<ChallengeGroup> findByInviteCodeWithLock(@Param("inviteCode") String inviteCode);
}
