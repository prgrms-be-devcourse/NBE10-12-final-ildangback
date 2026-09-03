package com.gommit.domain.challenge.service;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeMember;
import com.gommit.domain.challenge.entity.ChallengeMemberRole;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChallengeMemberService {
    private final ChallengeMemberRepository challengeMemberRepository;

    // 그룹 생성자를 챌린지멤버 등록
    public ChallengeMember createChallengeMember(Challenge challenge, Long userId, ChallengeMemberRole role) {
        ChallengeMember challengeMember = ChallengeMember.builder()
            .challenge(challenge)
            .userId(userId)
            .role(role)
            .build();

        return challengeMemberRepository.save(challengeMember);
    }
}
