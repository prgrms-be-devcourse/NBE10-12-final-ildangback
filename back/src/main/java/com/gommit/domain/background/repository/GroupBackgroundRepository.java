package com.gommit.domain.background.repository;

import com.gommit.domain.background.entity.GroupBackground;
import com.gommit.domain.background.entity.GroupBackgroundStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupBackgroundRepository extends JpaRepository<GroupBackground, Long> {

    List<GroupBackground> findAllByGroupId(Long groupId);

    Optional<GroupBackground> findByGroupIdAndStatus(Long groupId, GroupBackgroundStatus status);

    Optional<GroupBackground> findByGroupIdAndBackgroundId(Long groupId, Long backgroundId);

    boolean existsByGroupIdAndBackgroundId(Long groupId, Long backgroundId);

    boolean existsByBackgroundId(Long backgroundId);
}
