package com.gommit.domain.group.repository;

import com.gommit.domain.group.entity.ChallengeGroup;
import com.gommit.domain.group.entity.GroupStatus;
import com.gommit.domain.group.entity.Visibility;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChallengeGroupRepository extends JpaRepository<ChallengeGroup, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from ChallengeGroup g where g.id = :groupId")
    Optional<ChallengeGroup> findByIdWithLock(@Param("groupId") Long groupId);

    List<ChallengeGroup> findAllByVisibilityAndStatus(Visibility visibility, GroupStatus status);
}
