package com.gommit.domain.background.repository;

import com.gommit.domain.background.entity.BackgroundPurchaseRequest;
import com.gommit.domain.background.entity.PurchaseRequestStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BackgroundPurchaseRequestRepository extends JpaRepository<BackgroundPurchaseRequest, Long> {

    Optional<BackgroundPurchaseRequest> findByGroupIdAndStatus(Long groupId, PurchaseRequestStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from BackgroundPurchaseRequest r where r.id = :requestId")
    Optional<BackgroundPurchaseRequest> findWithLockById(@Param("requestId") Long requestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from BackgroundPurchaseRequest r where r.groupId = :groupId and r.status = :status")
    Optional<BackgroundPurchaseRequest> findWithLockByGroupIdAndStatus(
            @Param("groupId") Long groupId, @Param("status") PurchaseRequestStatus status);
}
