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
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChallengeLifecycleService {
    private final ChallengeRepository challengeRepository;
    private final ChallengeMemberRepository challengeMemberRepository;
    private final ChallengeGroupRepository challengeGroupRepository;

    @Transactional
    public void activateChallengesDueToday() {
        LocalDate today = LocalDate.now();
        List<Challenge> readyChallenges = challengeRepository.findAllByStatus(ChallengeStatus.READY);
        List<Challenge> challengesDueToday = readyChallenges.stream()
                .filter(challenge -> challenge.getStartDate().equals(today))
                .toList();
        Set<Long> groupIds =
                challengesDueToday.stream().map(Challenge::getGroupId).collect(Collectors.toSet());
        Map<Long, ChallengeGroup> groupMap = challengeGroupRepository.findAllById(groupIds).stream()
                .collect(Collectors.toMap(ChallengeGroup::getId, Function.identity()));
        for (Challenge challenge : challengesDueToday) {
            challenge.activate();
            ChallengeGroup group = groupMap.get(challenge.getGroupId());
            if (group == null) {
                throw new BusinessException(ErrorCode.GROUP_NOT_FOUND);
            }
            if (challenge.getSeqNo() == 1) {
                group.activate();
                continue;
            }
            // 연장 시즌이면 새 시즌 OWNER를 Group OWNER로 동기화
            ChallengeMember owner = challengeMemberRepository
                    .findByChallengeIdAndRole(challenge.getId(), ChallengeMemberRole.OWNER)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_OWNER));
            group.changeOwner(owner.getUserId());
        }
    }

    @Transactional
    public void endChallengesDueToday() {
        LocalDate today = LocalDate.now();
        List<Challenge> activeChallenges = challengeRepository.findAllByStatus(ChallengeStatus.ACTIVE);
        List<Challenge> challengesDueToday = activeChallenges.stream()
                .filter(challenge -> challenge.getEndDate().plusDays(1).equals(today))
                .toList();
        Set<Long> groupIdsToEnd = new HashSet<>();
        for (Challenge challenge : challengesDueToday) {
            challenge.end();
            Optional<Challenge> nextChallenge =
                    challengeRepository.findByGroupIdAndSeqNo(challenge.getGroupId(), challenge.getSeqNo() + 1);
            if (nextChallenge.isPresent()) {
                continue;
            }
            groupIdsToEnd.add(challenge.getGroupId());
        }
        Map<Long, ChallengeGroup> groupMap = challengeGroupRepository.findAllById(groupIdsToEnd).stream()
                .collect(Collectors.toMap(ChallengeGroup::getId, Function.identity()));
        for (Long groupId : groupIdsToEnd) {
            ChallengeGroup group = groupMap.get(groupId);
            if (group == null) {
                throw new BusinessException(ErrorCode.GROUP_NOT_FOUND);
            }
            group.end();
        }
    }
}
