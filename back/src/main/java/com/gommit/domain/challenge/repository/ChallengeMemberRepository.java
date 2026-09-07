package com.gommit.domain.challenge.repository;

import com.gommit.domain.challenge.entity.ChallengeMember;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChallengeMemberRepository extends JpaRepository<ChallengeMember, Long> {

    // 월간 머지 아카이브(내가 속한 챌린지 목록)를 만들 때 쓴다.
    List<ChallengeMember> findAllByUserId(Long userId);
}
