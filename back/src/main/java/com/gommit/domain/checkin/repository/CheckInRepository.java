package com.gommit.domain.checkin.repository;

import com.gommit.domain.checkin.entity.CheckIn;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {
    boolean existsByUserIdAndBusinessDate(Long userId, LocalDate businessDate);

    boolean existsByChallengeIdAndUserIdAndBusinessDate(Long challengeId, Long userId, LocalDate businessDate);
}
