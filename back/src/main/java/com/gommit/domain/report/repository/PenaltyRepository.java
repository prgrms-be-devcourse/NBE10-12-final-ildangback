package com.gommit.domain.report.repository;

import com.gommit.domain.report.entity.Penalty;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PenaltyRepository extends JpaRepository<Penalty, Long> {

    List<Penalty> findAllByReportId(Long reportId);

    List<Penalty> findAllByUserIdAndRevokedAtIsNull(Long userId);

    List<Penalty> findAllByReportIdIn(List<Long> reportIds);

    @Query("select p.userId as userId, count(p.id) as count from Penalty p"
            + " where p.userId in :userIds and p.revokedAt is null"
            + " group by p.userId")
    List<PenaltyCount> countByUserIds(@Param("userIds") List<Long> userIds);
}
