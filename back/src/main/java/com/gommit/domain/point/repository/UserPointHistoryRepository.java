package com.gommit.domain.point.repository;

import com.gommit.domain.point.entity.UserPointHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPointHistoryRepository extends JpaRepository<UserPointHistory, Long> {}
