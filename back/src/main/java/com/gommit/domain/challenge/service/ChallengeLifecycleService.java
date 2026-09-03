package com.gommit.domain.challenge.service;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeMember;
import com.gommit.domain.challenge.entity.ChallengeMemberRole;
import com.gommit.domain.challenge.entity.ChallengeStatus;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.challenge.repository.ChallengeRepository;
import com.gommit.domain.group.entity.ChallengeGroup;
import com.gommit.domain.group.repository.ChallengeGroupRepository;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChallengeLifecycleService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ChallengeRepository challengeRepository;
    private final ChallengeMemberRepository challengeMemberRepository;
    private final ChallengeGroupRepository challengeGroupRepository;

    @Transactional
    public void activateChallengesDueToday() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        List<Challenge> readyChallenges =
            challengeRepository.findAllByStatus(ChallengeStatus.READY);

        for (Challenge challenge : readyChallenges) {
            if (!challenge.getStartDate().equals(today)) {
                continue;
            }
            // Challenge READY -> ACTIVE
            challenge.activate();

            ChallengeGroup group = challengeGroupRepository.findById(challenge.getGroupId()).orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

            // 첫 시즌이 시작되는 경우 그룹도 READY -> ACTIVE
            if(challenge.getSeqNo() == 1) {
                group.activate();
                continue;
            }

            // 연장 시즌이면 새 시즌 OWNER를 Group OWNER로 동기화
            ChallengeMember owner = challengeMemberRepository.findByChallengeIdAndRole(challenge.getId(), ChallengeMemberRole.OWNER)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CHALLENGE_OWNER));

            group.changeOwner(owner.getUserId());
        }
    }

    @Transactional
    public void endChallengesDueToday() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        List<Challenge> activeChallenges = challengeRepository.findAllByStatus(ChallengeStatus.ACTIVE);

        for(Challenge challenge : activeChallenges) {
            // endDate의 다음날 04:00에 종료
            if(!challenge.getEndDate().plusDays(1).equals(today)) {
                continue;
            }

            // 현재 시즌 종료
            challenge.end();

            // 다음 시즌이 존재하는지 확인
            Optional<Challenge> nextChallenge = challengeRepository.findByGroupIdAndSeqNo(challenge.getGroupId(), challenge.getSeqNo() + 1);

            // 다음 시즌이 있으면 Group은 ACTIVE 유지
            if(nextChallenge.isPresent()) {
                continue;
            }

            // 다음 시즌이 없으면 Group 종료
            ChallengeGroup group = challengeGroupRepository.findById(challenge.getGroupId()).orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

            group.end();
        }
    }
}
