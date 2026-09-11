package com.gommit.domain.checkin.service;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeMember;
import com.gommit.domain.challenge.entity.ChallengeMemberStatus;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.challenge.service.ChallengeProgressCalculator;
import com.gommit.domain.checkin.entity.CheckIn;
import com.gommit.domain.checkin.repository.CheckInRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupCompletionQueryService {
    private final CheckInRepository checkInRepository;
    private final ChallengeMemberRepository challengeMemberRepository;
    private final ChallengeProgressCalculator progressCalculator;

    public int countCompletedDays(Challenge challenge, LocalDate today) {
        LocalDate end = today.isBefore(challenge.getEndDate()) ? today : challenge.getEndDate();
        if (end.isBefore(challenge.getStartDate())) {
            return 0;
        }

        // 기존 갤러리 기간 조회를 재사용한다. 페이지/count 쿼리 없이 시즌 기록과 멤버 이력을 각 1회 조회.
        List<CheckIn> checkIns = checkInRepository.findGallery(
                challenge.getId(), challenge.getStartDate(), end, null, null, null, null, Pageable.unpaged());
        List<ChallengeMember> members = challengeMemberRepository.findAllByChallengeId(challenge.getId());
        Map<LocalDate, Map<Long, Integer>> counts = new HashMap<>();
        Set<LocalDate> completedDays = new HashSet<>();
        int target = challenge.getDailyCheckInCount();

        // 현재 ACTIVE 명단을 과거에 소급하지 않는다. 기존 스트릭처럼 인증 제출 당시 ACTIVE 전원 완료만 인정한다.
        // 이탈 자체는 성공을 발생시키지 않으며, 이미 성공한 날짜는 이후 이탈/강퇴로 바뀌지 않는다.
        for (CheckIn checkIn : checkIns.stream()
                .sorted(Comparator.comparing(CheckIn::getCreatedAt).thenComparing(CheckIn::getId))
                .toList()) {
            LocalDate date = checkIn.getBusinessDate();
            if (completedDays.contains(date) || !progressCalculator.isCheckInDay(challenge, date)) {
                continue;
            }
            Map<Long, Integer> dayCounts = counts.computeIfAbsent(date, ignored -> new HashMap<>());
            int count = dayCounts.merge(checkIn.getUserId(), 1, Integer::sum);
            if (count < target) {
                continue;
            }
            List<Long> activeIds = members.stream()
                    .filter(member -> wasActiveAt(member, checkIn.getCreatedAt()))
                    .map(ChallengeMember::getUserId)
                    .toList();
            if (activeIds.contains(checkIn.getUserId())
                    && activeIds.stream().allMatch(id -> dayCounts.getOrDefault(id, 0) >= target)) {
                completedDays.add(date);
            }
        }
        return completedDays.size();
    }

    private boolean wasActiveAt(ChallengeMember member, LocalDateTime submittedAt) {
        if (member.getCreatedAt().isAfter(submittedAt)) {
            return false;
        }
        if (member.getLeftAt() != null) {
            return submittedAt.isBefore(member.getLeftAt());
        }
        // LEFT/KICKED인데 이탈 시각이 없는 불완전한 이력은 ACTIVE로 추측하지 않는다.
        return member.getStatus() == ChallengeMemberStatus.ACTIVE;
    }
}
