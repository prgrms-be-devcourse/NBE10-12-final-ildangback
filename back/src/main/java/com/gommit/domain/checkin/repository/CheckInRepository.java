package com.gommit.domain.checkin.repository;

import com.gommit.domain.checkin.entity.CheckIn;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

    @Query("""
            select c.userId from CheckIn c
            where c.challengeId = :challengeId and c.businessDate = :businessDate
            group by c.userId
            having count(c) >= :target
            """)
    List<Long> findCompletedUserIds(
            @Param("challengeId") Long challengeId,
            @Param("businessDate") LocalDate businessDate,
            @Param("target") int target);
}
