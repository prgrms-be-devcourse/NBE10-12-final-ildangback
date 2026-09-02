package com.gommit.domain.point.repository;

import com.gommit.domain.point.entity.GroupPoint;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupPointRepository extends JpaRepository<GroupPoint, Long> {

    // 지급/차감 시 이 행을 잠근다(SELECT ... FOR UPDATE). 같은 그룹에 대한 동시 호출을 직렬화한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from GroupPoint p where p.groupId = :groupId")
    Optional<GroupPoint> findWithLockByGroupId(@Param("groupId") Long groupId);

    Optional<GroupPoint> findByGroupId(Long groupId);

    boolean existsByGroupId(Long groupId);

    // 없으면 0원 잔액 행을 만들고 있으면 무시한다(멱등, 동시 호출해도 예외 없음).
    @Modifying(clearAutomatically = true)
    @Query(
            value = "insert into group_points (group_id, balance, created_at, updated_at) "
                    + "values (:groupId, 0, :now, :now) "
                    + "on duplicate key update group_id = group_id",
            nativeQuery = true)
    void insertZeroBalanceIfAbsent(@Param("groupId") Long groupId, @Param("now") LocalDateTime now);
}
