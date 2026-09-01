package com.gommit.domain.point.repository;

import com.gommit.domain.point.entity.UserPoint;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserPointRepository extends JpaRepository<UserPoint, Long> {

    // 지급/차감 시 이 행을 잠근다(SELECT ... FOR UPDATE). 같은 유저에 대한 동시 호출을 직렬화한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from UserPoint p where p.userId = :userId")
    Optional<UserPoint> findWithLockByUserId(@Param("userId") Long userId);

    Optional<UserPoint> findByUserId(Long userId);
}
