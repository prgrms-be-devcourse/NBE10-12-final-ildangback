package com.gommit.domain.report.repository;

import com.gommit.domain.report.entity.Appeal;
import com.gommit.domain.report.entity.AppealStatus;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppealRepository extends JpaRepository<Appeal, Long> {

    boolean existsByReportId(Long reportId);

    List<Appeal> findAllByReportIdIn(List<Long> reportIds);

    @Query("select a from Appeal a"
            + " where (:status is null or a.status = :status)"
            + " and (:cursor is null or a.id < :cursor)"
            + " order by a.id desc")
    List<Appeal> findPage(@Param("status") AppealStatus status, @Param("cursor") Long cursor, Pageable pageable);
}
