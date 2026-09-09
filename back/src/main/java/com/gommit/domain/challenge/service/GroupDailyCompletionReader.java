package com.gommit.domain.challenge.service;

import com.gommit.domain.checkin.repository.CheckInRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 그룹 하루 전원 완료 판정을 위해 "지금까지 커밋된" 인증 상태를 읽는다.
// 호출자(인증 submit 트랜잭션)는 REPEATABLE READ 스냅샷에 묶여 다른 멤버가 방금 커밋한 인증을 못 본다.
// 새 트랜잭션(REQUIRES_NEW)으로 읽어 새 스냅샷을 얻는다 — 잠금 없이도 최신 커밋을 본다.
// 대신 호출자 자신의 이번 인증은 아직 커밋 전이라 여기서 안 보이므로, 호출부에서 따로 더한다.
@Component
@RequiredArgsConstructor
public class GroupDailyCompletionReader {

    private final CheckInRepository checkInRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Set<Long> completedMemberIds(
            Long challengeId, LocalDate businessDate, int target, Collection<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(checkInRepository.findCompletedUserIds(challengeId, businessDate, target, memberIds));
    }
}
