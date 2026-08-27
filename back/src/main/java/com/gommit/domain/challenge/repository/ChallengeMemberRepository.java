package com.gommit.domain.challenge.repository;

import com.gommit.domain.challenge.entity.ChallengeMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChallengeMemberRepository extends JpaRepository<ChallengeMember, Long> {}
