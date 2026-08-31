package com.gommit.domain.group.repository;

import com.gommit.domain.group.entity.ChallengeGroup;
import com.gommit.domain.group.entity.GroupStatus;
import com.gommit.domain.group.entity.Visibility;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChallengeGroupRepository extends JpaRepository<ChallengeGroup, Long> {
    List<ChallengeGroup> findAllByVisibilityAndStatus(Visibility visibility, GroupStatus status);
}
