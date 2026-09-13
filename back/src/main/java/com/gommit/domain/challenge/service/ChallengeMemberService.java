package com.gommit.domain.challenge.service;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeMember;
import com.gommit.domain.challenge.entity.ChallengeMemberRole;
import com.gommit.domain.challenge.entity.ChallengeMemberStatus;
import com.gommit.domain.challenge.entity.ChallengeStatus;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChallengeMemberService {
    private final ChallengeMemberRepository challengeMemberRepository;
    private final ChallengeProgressCalculator challengeProgressCalculator;

    public ChallengeMember createChallengeMember(Challenge challenge, Long userId, ChallengeMemberRole role) {
        ChallengeMember challengeMember = ChallengeMember.builder()
                .challenge(challenge)
                .userId(userId)
                .role(role)
                .build();
        return challengeMemberRepository.save(challengeMember);
    }

    public LocalDate findLastRequiredCheckInDay(Long userId, LocalDate businessDate) {
        return challengeMemberRepository.findAllByUserIdAndStatus(userId, ChallengeMemberStatus.ACTIVE).stream()
                .filter(member -> member.getChallenge().getStatus() == ChallengeStatus.ACTIVE)
                .map(member -> challengeProgressCalculator.previousCheckInDay(member.getChallenge(), businessDate))
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }
}
