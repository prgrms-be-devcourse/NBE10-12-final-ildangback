package com.gommit.domain.checkin.repository;

import com.gommit.domain.checkin.entity.DailyLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyLogRepository extends JpaRepository<DailyLog, Long> {}
