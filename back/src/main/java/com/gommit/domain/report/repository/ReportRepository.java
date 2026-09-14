package com.gommit.domain.report.repository;

import com.gommit.domain.report.entity.Report;
import com.gommit.domain.report.entity.ReportStatus;
import com.gommit.domain.report.entity.ReportTargetType;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Report r where r.id = :reportId")
    Optional<Report> findByIdWithLock(@Param("reportId") Long reportId);

    boolean existsByReporterIdAndTargetTypeAndTargetIdAndStatus(
            Long reporterId, ReportTargetType targetType, Long targetId, ReportStatus status);

    List<Report> findAllByIdIn(List<Long> ids);

    @Query("select r from Report r"
            + " where r.targetUserId = :targetUserId"
            + " and r.status = com.gommit.domain.report.entity.ReportStatus.ACCEPTED"
            + " and (:cursor is null or r.id < :cursor)"
            + " order by r.id desc")
    List<Report> findPageByTargetUser(
            @Param("targetUserId") Long targetUserId, @Param("cursor") Long cursor, Pageable pageable);

    @Query("select r from Report r"
            + " where (:status is null or r.status = :status)"
            + " and (:cursor is null or r.id < :cursor)"
            + " order by r.id desc")
    List<Report> findPage(@Param("status") ReportStatus status, @Param("cursor") Long cursor, Pageable pageable);
}
