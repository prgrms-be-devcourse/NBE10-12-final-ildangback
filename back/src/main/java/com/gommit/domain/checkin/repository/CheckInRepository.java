package com.gommit.domain.checkin.repository;

import com.gommit.domain.checkin.entity.CheckIn;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {}
