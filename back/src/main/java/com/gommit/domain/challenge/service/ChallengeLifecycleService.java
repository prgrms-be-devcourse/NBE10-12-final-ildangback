package com.gommit.domain.challenge.service;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeMember;
import com.gommit.domain.challenge.entity.ChallengeMemberRole;
import com.gommit.domain.challenge.entity.ChallengeMemberStatus;
import com.gommit.domain.challenge.entity.ChallengeStatus;
import com.gommit.domain.challenge.event.ChallengeEndedEvent;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.challenge.repository.ChallengeRepository;
import com.gommit.domain.group.entity.ChallengeGroup;
import com.gommit.domain.group.repository.ChallengeGroupRepository;
import com.gommit.domain.notification.entity.Notification;
import com.gommit.domain.notification.entity.NotificationType;
import com.gommit.domain.notification.repository.NotificationRepository;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import com.gommit.global.time.BusinessClock;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChallengeLifecycleService {
    private final ChallengeRepository challengeRepository;
    private final ChallengeMemberRepository challengeMemberRepository;
    private final ChallengeGroupRepository challengeGroupRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationRepository notificationRepository;
    private final BusinessClock businessClock;

    @Transactional
    public void activateChallengesDueToday() {
        LocalDate today = businessClock.today();
        // READY 조회에 잠금을 적용해 동시 활성화와 시작 알림 생성을 직렬화한다.
        List<Challenge> readyChallenges = challengeRepository.findReadyForActivation();
        // startDate가 오늘이거나 이미 지났으면 활성화한다(정확히 그날만 보면 배치가
        // 하루라도 못 돈 사이 놓친 챌린지는 영영 못 따라잡는다).
        List<Challenge> challengesDueToday = readyChallenges.stream()
                .filter(challenge -> !challenge.getStartDate().isAfter(today))
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
                notifySeasonStarted(challenge);
                continue;
            }
            // 연장 시즌이면 새 시즌 OWNER를 Group OWNER로 동기화
            ChallengeMember owner = challengeMemberRepository
                    .findByChallengeIdAndRole(challenge.getId(), ChallengeMemberRole.OWNER)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_OWNER));
            group.changeOwner(owner.getUserId());
            notifySeasonStarted(challenge);
        }
    }

    private void notifySeasonStarted(Challenge challenge) {
        for (ChallengeMember member : challengeMemberRepository.findAllByChallengeIdAndStatus(
                challenge.getId(), ChallengeMemberStatus.ACTIVE)) {
            if (notificationRepository.existsByUserIdAndTypeAndRefId(
                    member.getUserId(), NotificationType.SEASON_STARTED, challenge.getId())) continue;
            notificationRepository.save(new Notification(
                    member.getUserId(),
                    NotificationType.SEASON_STARTED,
                    "새 시즌이 시작됐어요!",
                    "새 시즌이 시작됐어요! 오늘부터 다시 인증을 시작해보세요 🔥",
                    challenge.getId()));
        }
    }

    @Transactional
    public void endChallengesDueToday() {
        LocalDate today = businessClock.today();
        List<Challenge> activeChallenges = challengeRepository.findAllByStatus(ChallengeStatus.ACTIVE);
        // endDate가 지났으면(오늘 포함 안 함) 종료한다 - 활성화와 동일한 이유로
        // 정확히 다음날만 보면 안 된다.
        List<Challenge> challengesDueToday = activeChallenges.stream()
                .filter(challenge -> challenge.getEndDate().isBefore(today))
                .toList();
        Set<Long> groupIdsToEnd = new HashSet<>();
        for (Challenge challenge : challengesDueToday) {
            challenge.end();
            // 최종 머지 생성은 무거운 집계/포인트 지급을 동반해서 상태 전환
            // 트랜잭션 안에서 동기 실행하면 락 점유가 길어진다 - 커밋 후 비동기로
            // 분리한다(ChallengeEndedEventListener).
            eventPublisher.publishEvent(new ChallengeEndedEvent(challenge.getId()));
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
